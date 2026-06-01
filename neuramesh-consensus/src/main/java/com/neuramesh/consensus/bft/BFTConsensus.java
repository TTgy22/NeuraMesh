package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.block.BlockProducer;
import com.neuramesh.consensus.block.BlockStore;
import com.neuramesh.consensus.exception.ConsensusException;
import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PBFT 三阶段共识状态机（单节点视角）。
 *
 * <p>正常路径：{@code startConsensus(h)} → 提案人 {@code propose()} 广播 PrePrepare →
 * 各节点验证后广播 PREPARE → PREPARE 达到法定人数后广播 COMMIT → COMMIT 达到法定人数后
 * {@code finalizeBlock()} 写入 {@link BlockStore} 并标记最终性。
 *
 * <p>拜占庭防护：对同一高度，若提案人发送了与已接受区块不同的第二个 PrePrepare（等价物 / equivocation），
 * 拒绝并触发视图变更（切换提案人）。
 *
 * <p>消息传输经 {@link ConsensusBroadcaster} 抽象；本类不直接耦合网络。状态读写使用并发结构，
 * 投票回调不持有粗粒度锁。
 *
 * <p>视图变更（超时切换提案人）以 {@code height + view} 作为提案人选择 round 实现；
 * 阶段超时定时器为可选项，确定性测试中通过 {@link #triggerViewChange()} 显式驱动。
 */
public final class BFTConsensus {

    private static final Logger LOG = LoggerFactory.getLogger(BFTConsensus.class);

    private final byte[] localId;
    private final KeyPair localKeys;
    private final ValidatorSet validators;
    private final ProposerSelector proposerSelector;
    private final VoteCollector voteCollector;
    private final BlockProducer blockProducer;
    private final BlockStore blockStore;
    private final BlockFinality finality;
    private final ConsensusBroadcaster broadcaster;

    private volatile long currentHeight = -1;
    private volatile int view = 0;
    private volatile ConsensusState state = ConsensusState.IDLE;
    private final AtomicInteger equivocationCount = new AtomicInteger(0);

    /** height -> 已接受的区块哈希（用于等价物检测）。 */
    private final Map<Long, byte[]> acceptedProposalHash = new ConcurrentHashMap<>();
    /** blockHashHex -> 已接受的区块（用于最终化取回）。 */
    private final Map<String, Block> proposedBlocks = new ConcurrentHashMap<>();
    private final Set<String> preparedSent = ConcurrentHashMap.newKeySet();
    private final Set<String> committedSent = ConcurrentHashMap.newKeySet();

    private volatile LongConsumer onFinalized = h -> { };

    public BFTConsensus(KeyPair localKeys, ValidatorSet validators,
                        ProposerSelector proposerSelector, BlockProducer blockProducer,
                        BlockStore blockStore, BlockFinality finality,
                        ConsensusBroadcaster broadcaster) {
        this.localKeys = java.util.Objects.requireNonNull(localKeys, "localKeys");
        this.localId = CryptoUtils.toAddress(localKeys.getPublic());
        this.validators = java.util.Objects.requireNonNull(validators, "validators");
        this.proposerSelector = java.util.Objects.requireNonNull(proposerSelector, "proposerSelector");
        this.blockProducer = java.util.Objects.requireNonNull(blockProducer, "blockProducer");
        this.blockStore = java.util.Objects.requireNonNull(blockStore, "blockStore");
        this.finality = java.util.Objects.requireNonNull(finality, "finality");
        this.broadcaster = java.util.Objects.requireNonNull(broadcaster, "broadcaster");
        this.voteCollector = new VoteCollector(validators.quorum());
    }

    /**
     * 设置区块最终化回调（用于自动推进到下一高度）。
     *
     * @param callback 入参为已最终化的高度
     */
    public void setOnFinalized(LongConsumer callback) {
        if (callback != null) {
            this.onFinalized = callback;
        }
    }

    /**
     * 开始某高度的共识。若本节点为该 (height, view) 的提案人，则立即提案。
     *
     * @param height 高度
     */
    public void startConsensus(long height) {
        this.currentHeight = height;
        this.view = 0;
        this.state = ConsensusState.PROPOSING;
        Validator proposer = currentProposer();
        LOG.debug("[{}] startConsensus h={} proposer={}", shortId(), height, proposer.getNodeIdHex());
        if (Arrays.equals(proposer.getNodeId(), localId)) {
            propose();
        }
    }

    private Validator currentProposer() {
        return proposerSelector.selectProposer(currentHeight + view, validators.getValidators());
    }

    private void propose() {
        Block prev = blockStore.getLatestBlock();
        byte[] prevHash = (prev == null) ? new byte[32] : prev.getHash();
        Block block = blockProducer.produce(currentHeight, prevHash, System.currentTimeMillis());
        byte[] sig = CryptoUtils.sign(block.getHash(), localKeys.getPrivate());
        PrePrepare pp = new PrePrepare(currentHeight, block, localId, sig);
        LOG.info("[{}] 提案 h={} blockHash={}", shortId(), currentHeight,
                CryptoUtils.toHex(block.getHash()).substring(0, 12));
        handlePrePrepare(pp);
        broadcaster.broadcastPrePrepare(pp);
    }

    /**
     * 处理收到的 PrePrepare。
     *
     * @param pp 提案
     */
    public void onPrePrepare(PrePrepare pp) {
        handlePrePrepare(pp);
    }

    private void handlePrePrepare(PrePrepare pp) {
        if (pp.getHeight() != currentHeight) {
            return;
        }
        Validator expected = currentProposer();
        if (!Arrays.equals(expected.getNodeId(), pp.getProposerId())) {
            LOG.warn("[{}] 提案人不匹配，拒绝 h={}", shortId(), pp.getHeight());
            return;
        }
        if (!pp.verify(validators)) {
            LOG.warn("[{}] 提案签名无效，拒绝 h={}", shortId(), pp.getHeight());
            return;
        }
        byte[] blockHash = pp.getBlock().getHash();
        byte[] prevAccepted = acceptedProposalHash.get(pp.getHeight());
        if (prevAccepted != null) {
            if (!Arrays.equals(prevAccepted, blockHash)) {
                equivocationCount.incrementAndGet();
                LOG.warn("[{}] 检测到提案人等价物（冲突区块）h={}，触发视图变更", shortId(), pp.getHeight());
                triggerViewChange();
            }
            return;
        }
        acceptedProposalHash.put(pp.getHeight(), blockHash);
        proposedBlocks.put(CryptoUtils.toHex(blockHash), pp.getBlock());
        state = ConsensusState.PREPARING;
        sendPrepare(blockHash);
        // 若此前已收到足量投票（乱序到达），补判一次
        checkPrepareQuorum(blockHash);
        checkCommitQuorum(blockHash);
    }

    private void sendPrepare(byte[] blockHash) {
        String key = CryptoUtils.toHex(blockHash);
        if (!preparedSent.add(key)) {
            return;
        }
        Vote vote = makeVote(VoteType.PREPARE, blockHash);
        voteCollector.addVote(vote);
        checkPrepareQuorum(blockHash);
        broadcaster.broadcastPrepare(vote);
    }

    /**
     * 处理收到的 PREPARE 投票。
     *
     * @param vote 投票
     */
    public void onPrepare(Vote vote) {
        if (vote.getType() != VoteType.PREPARE || !vote.verify(validators)) {
            return;
        }
        voteCollector.addVote(vote);
        checkPrepareQuorum(vote.getBlockHash());
    }

    private void checkPrepareQuorum(byte[] blockHash) {
        if (voteCollector.hasQuorum(CryptoUtils.toHex(blockHash), VoteType.PREPARE)) {
            sendCommit(blockHash);
        }
    }

    private void sendCommit(byte[] blockHash) {
        String key = CryptoUtils.toHex(blockHash);
        if (!committedSent.add(key)) {
            return;
        }
        state = ConsensusState.COMMITTING;
        Vote vote = makeVote(VoteType.COMMIT, blockHash);
        voteCollector.addVote(vote);
        checkCommitQuorum(blockHash);
        broadcaster.broadcastCommit(vote);
    }

    /**
     * 处理收到的 COMMIT 投票。
     *
     * @param vote 投票
     */
    public void onCommit(Vote vote) {
        if (vote.getType() != VoteType.COMMIT || !vote.verify(validators)) {
            return;
        }
        voteCollector.addVote(vote);
        checkCommitQuorum(vote.getBlockHash());
    }

    private void checkCommitQuorum(byte[] blockHash) {
        if (voteCollector.hasQuorum(CryptoUtils.toHex(blockHash), VoteType.COMMIT)) {
            finalizeBlock(blockHash);
        }
    }

    private void finalizeBlock(byte[] blockHash) {
        if (finality.isFinalized(currentHeight)) {
            return;
        }
        Block block = proposedBlocks.get(CryptoUtils.toHex(blockHash));
        if (block == null) {
            return;
        }
        blockStore.put(block);
        finality.markFinalized(block);
        state = ConsensusState.FINALIZED;
        LOG.info("[{}] 区块最终化 h={} blockHash={}", shortId(), currentHeight,
                CryptoUtils.toHex(blockHash).substring(0, 12));
        onFinalized.accept(currentHeight);
    }

    /**
     * 视图变更（超时或检测到作恶时切换提案人）。简化版：view++ 并允许新提案人重新提案。
     */
    public void triggerViewChange() {
        view++;
        state = ConsensusState.PROPOSING;
        acceptedProposalHash.remove(currentHeight);
        Validator proposer = currentProposer();
        LOG.info("[{}] 视图变更 h={} view={} 新提案人={}", shortId(), currentHeight, view,
                proposer.getNodeIdHex());
        if (Arrays.equals(proposer.getNodeId(), localId) && !finality.isFinalized(currentHeight)) {
            propose();
        }
    }

    private Vote makeVote(VoteType type, byte[] blockHash) {
        byte[] sig = CryptoUtils.sign(Vote.signingBytes(type, blockHash), localKeys.getPrivate());
        return new Vote(type, blockHash, localId, sig);
    }

    private String shortId() {
        return CryptoUtils.toHex(localId).substring(0, 6);
    }

    public byte[] getLocalId() {
        return localId.clone();
    }

    public ConsensusState getState() {
        return state;
    }

    public int getView() {
        return view;
    }

    public long getCurrentHeight() {
        return currentHeight;
    }

    public int getEquivocationCount() {
        return equivocationCount.get();
    }

    public BlockFinality getFinality() {
        return finality;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public VoteCollector getVoteCollector() {
        return voteCollector;
    }

    public boolean isValidator() {
        return validators.isValidator(localId);
    }
}
