package com.neuramesh.api.service;

import com.neuramesh.api.dto.BlockInfoDTO;
import com.neuramesh.api.dto.TxInfoDTO;
import com.neuramesh.consensus.TxPool;
import com.neuramesh.consensus.bft.BFTConsensus;
import com.neuramesh.consensus.bft.BlockFinality;
import com.neuramesh.consensus.bft.ConsensusBroadcaster;
import com.neuramesh.consensus.bft.PrePrepare;
import com.neuramesh.consensus.bft.ProposerSelector;
import com.neuramesh.consensus.bft.Validator;
import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.consensus.bft.Vote;
import com.neuramesh.consensus.block.BlockProducer;
import com.neuramesh.consensus.block.BlockStore;
import com.neuramesh.consensus.block.InMemoryBlockStore;
import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.vm.Attestation;
import com.neuramesh.vm.StateMachine;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;
import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 链上下文服务：整合 <strong>真实</strong> 共识管线——交易池 → BFT 出块 → 区块存储 → 状态机执行。
 *
 * <p>每笔交易经 {@link TxPool} 入池，由单验证者 {@link BFTConsensus}（自达法定人数：quorum=1）
 * 真实走完 PBFT 三阶段（PrePrepare/Prepare/Commit，含真实 ECDSA 签名与 {@code VoteCollector} 计票），
 * 最终化写入 {@link BlockStore}，并在最终化回调中由 {@link StateMachine} 执行、变更 {@link GlobalState}。
 *
 * <p>{@link #applyTx} 同步驱动一轮共识并执行，保持调用方语义；区块/交易从真实 {@link BlockStore} 与
 * 交易索引读取，非静态返回。
 *
 * <p>验证者解耦：BFT 出块用单验证者自达共识；状态机 WEIGHT_UPDATE 见证用独立的 4 验证者集
 * （二者关注点不同：前者区块级共识，后者交易级背书）。
 */
@Service
public class ChainService {

    private static final Logger LOG = LoggerFactory.getLogger(ChainService.class);

    /** 交易生命周期状态。 */
    public static final String TX_PENDING = "pending";
    public static final String TX_PROPOSED = "proposed";
    public static final String TX_FINALIZED = "finalized";
    public static final String TX_EXECUTED = "executed";
    public static final String TX_REJECTED = "rejected";

    private final GlobalState state = new GlobalState();
    private final Map<String, Transaction> txIndex = new ConcurrentHashMap<>();
    private final Map<String, String> txStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> txHeight = new ConcurrentHashMap<>();
    private final List<KeyPair> validatorKeys = new ArrayList<>();

    // 真实共识管线组件
    private final TxPool txPool = new TxPool();
    private final BlockStore blockStore = new InMemoryBlockStore();
    private final BlockFinality finality = new BlockFinality();

    private ValidatorSet validators;      // 状态机见证用（4 验证者）
    private StateMachine stateMachine;
    private BFTConsensus consensus;        // 出块用（单验证者自达共识）
    private long nextHeight = 0;
    private VMException pendingError;      // 最终化执行期捕获的异常，回传调用方

    @PostConstruct
    void init() {
        // 状态机见证验证者集（4 个，WEIGHT_UPDATE 需 ≥2 一致背书）
        List<Validator> vs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            validatorKeys.add(kp);
            vs.add(new Validator(CryptoUtils.toAddress(kp.getPublic()), kp.getPublic(), 1, 0));
        }
        this.validators = new ValidatorSet(vs);
        this.stateMachine = StateMachine.standard(validators);

        // BFT 出块验证者集（单验证者 → quorum=1 → 自达共识，同步最终化）
        KeyPair bftKey = CryptoUtils.generateKeyPair();
        ValidatorSet bftValidators = new ValidatorSet(List.of(
                new Validator(CryptoUtils.toAddress(bftKey.getPublic()), bftKey.getPublic(), 1, 0)));
        BlockProducer producer = new BlockProducer(txPool);
        ConsensusBroadcaster noop = new ConsensusBroadcaster() {
            @Override public void broadcastPrePrepare(PrePrepare p) { }
            @Override public void broadcastPrepare(Vote v) { }
            @Override public void broadcastCommit(Vote v) { }
        };
        this.consensus = new BFTConsensus(bftKey, bftValidators, new ProposerSelector(0L),
                producer, blockStore, finality, noop);
        this.consensus.setOnFinalized(this::onBlockFinalized);

        LOG.info("ChainService 初始化：状态机 {} 验证者 + 单验证者 BFT 出块管线", validators.size());
    }

    public GlobalState state() {
        return state;
    }

    public ValidatorSet validators() {
        return validators;
    }

    public long nonceOf(byte[] address) {
        var acc = state.getAccount(address);
        return acc == null ? 0 : acc.getNonce();
    }

    public Attestation attest(int validatorIndex, byte[] targetNodeId, double claimedScore) {
        KeyPair kp = validatorKeys.get(validatorIndex);
        byte[] sig = CryptoUtils.sign(
                Attestation.signingBytes(targetNodeId, claimedScore), kp.getPrivate());
        return new Attestation(CryptoUtils.toAddress(kp.getPublic()), claimedScore, 1L, sig);
    }

    /**
     * 提交交易并驱动一轮真实共识：入池 → BFT 打包/三阶段/最终化 → 状态机执行。
     *
     * @param tx 交易
     * @return 已最终化并执行该交易的区块
     */
    public synchronized Block applyTx(Transaction tx) {
        String txHex = CryptoUtils.toHex(tx.getTxId());
        if (!txPool.addTransaction(tx)) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "交易入池失败（重复或非法）: " + txHex);
        }
        txStatus.put(txHex, TX_PENDING);

        long height = nextHeight;
        pendingError = null;
        consensus.startConsensus(height);   // 单验证者：同步走完三阶段并触发 onBlockFinalized
        nextHeight = blockStore.currentHeight() + 1;

        if (pendingError != null) {
            VMException e = pendingError;
            pendingError = null;
            throw e;                         // 状态机执行失败：状态已回滚，异常回传调用方
        }
        return blockStore.get(height);
    }

    /** BFT 最终化回调：对区块内交易顺序执行状态机，更新索引与生命周期状态。 */
    private void onBlockFinalized(long height) {
        Block block = blockStore.get(height);
        if (block == null) {
            return;
        }
        for (Transaction tx : block.getTransactions()) {
            String txHex = CryptoUtils.toHex(tx.getTxId());
            txIndex.put(txHex, tx);
            txHeight.put(txHex, height);
            txStatus.put(txHex, TX_FINALIZED);
            try {
                stateMachine.apply(tx, state);
                txStatus.put(txHex, TX_EXECUTED);
            } catch (VMException e) {
                txStatus.put(txHex, TX_REJECTED);
                pendingError = e;            // 由 applyTx 回传
                LOG.warn("区块 {} 内交易 {} 执行失败：{}", height, txHex.substring(0, 12), e.getKind());
            }
        }
    }

    /**
     * 交易生命周期状态（pending/proposed/finalized/executed/rejected）。
     *
     * @param hashHex 交易哈希 hex（可带 0x）
     * @return 状态；未知返回 null
     */
    public String txLifecycle(String hashHex) {
        String key = strip(hashHex);
        return txStatus.get(key);
    }

    /** 测试/创世注资（直写账户，演示用）。 */
    public synchronized void fund(byte[] address, long amount) {
        state.credit(address, amount);
    }

    public long balanceOf(byte[] address) {
        var acc = state.getAccount(address);
        return acc == null ? 0 : acc.getBalance();
    }

    /**
     * 最新区块摘要（从真实 {@link BlockStore} 按高度倒序读取，最多 limit 个）。
     */
    public synchronized List<BlockInfoDTO> latestBlocks(int limit) {
        List<BlockInfoDTO> out = new ArrayList<>();
        long top = blockStore.currentHeight();
        for (long h = top; h >= 0 && out.size() < limit; h--) {
            Block b = blockStore.get(h);
            if (b == null) {
                continue;
            }
            out.add(new BlockInfoDTO(b.getHeight(), CryptoUtils.toHex(b.getHash()),
                    CryptoUtils.toHex(b.getPrevHash()), b.getTimestamp(), b.getTransactions().size()));
        }
        return out;
    }

    /**
     * 按交易哈希查询（从真实交易索引）。
     *
     * @param hashHex 交易哈希 hex
     * @return 详情，找不到返回 null
     */
    public TxInfoDTO findTx(String hashHex) {
        Transaction tx = txIndex.get(strip(hashHex));
        if (tx == null) {
            return null;
        }
        return new TxInfoDTO(CryptoUtils.toHex(tx.getTxId()), tx.getType().name(),
                CryptoUtils.toHex(tx.getFrom()), CryptoUtils.toHex(tx.getTo()),
                tx.getNonce(), tx.getTimestamp());
    }

    public NodeState nodeProfile(byte[] nodeId) {
        return state.getNode(nodeId);
    }

    public int blockHeight() {
        long h = blockStore.currentHeight();
        return (int) (h + 1);
    }

    /**
     * 网络聚合统计（真实：区块数/交易数/节点/账户/权重/收益/余额）。
     */
    public synchronized com.neuramesh.api.dto.ChainStatsDTO stats() {
        double totalWeight = 0;
        long totalEarned = 0;
        var nodes = state.allNodes();
        for (NodeState n : nodes) {
            totalWeight += n.getTotalWeight();
            totalEarned += n.getTotalEarned();
        }
        return new com.neuramesh.api.dto.ChainStatsDTO(
                blockHeight(), txIndex.size(), nodes.size(), state.accountCount(),
                totalWeight, totalEarned, state.totalBalance());
    }

    private static String strip(String hashHex) {
        String k = hashHex.startsWith("0x") ? hashHex.substring(2) : hashHex;
        return k.toLowerCase();
    }
}
