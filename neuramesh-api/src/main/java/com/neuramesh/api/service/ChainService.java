package com.neuramesh.api.service;

import com.neuramesh.api.dto.BlockInfoDTO;
import com.neuramesh.api.dto.TxInfoDTO;
import com.neuramesh.consensus.bft.Validator;
import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.vm.Attestation;
import com.neuramesh.vm.StateMachine;
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
 * 链上下文服务：整合 P2 验证者集、P3 状态机/全局状态，维护内存区块链与交易索引。
 *
 * <p>每笔成功交易封装为一个区块（演示用，单交易区块），构成可浏览的哈希链。
 * 作为 Spring 单例，串行 {@link #applyTx} 保证状态一致。
 *
 * <p>债务：状态/区块仅内存（InMemory），RocksDB 持久化与多交易打包出块为后续 Pause 装配。
 */
@Service
public class ChainService {

    private static final Logger LOG = LoggerFactory.getLogger(ChainService.class);

    private final GlobalState state = new GlobalState();
    private final Map<String, Transaction> txIndex = new ConcurrentHashMap<>();
    private final List<Block> blocks = new ArrayList<>();
    private final List<KeyPair> validatorKeys = new ArrayList<>();

    private ValidatorSet validators;
    private StateMachine stateMachine;
    private byte[] lastHash = new byte[32];

    @PostConstruct
    void init() {
        List<Validator> vs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            KeyPair kp = CryptoUtils.generateKeyPair();
            validatorKeys.add(kp);
            vs.add(new Validator(CryptoUtils.toAddress(kp.getPublic()), kp.getPublic(), 1, 0));
        }
        this.validators = new ValidatorSet(vs);
        this.stateMachine = StateMachine.standard(validators);
        LOG.info("ChainService 初始化：{} 个验证者", validators.size());
    }

    public GlobalState state() {
        return state;
    }

    public ValidatorSet validators() {
        return validators;
    }

    /**
     * 当前账户 nonce（不存在视为 0）。
     */
    public long nonceOf(byte[] address) {
        var acc = state.getAccount(address);
        return acc == null ? 0 : acc.getNonce();
    }

    /**
     * 生成第 i 个验证者对某节点声明分数的见证签名。
     */
    public Attestation attest(int validatorIndex, byte[] targetNodeId, double claimedScore) {
        KeyPair kp = validatorKeys.get(validatorIndex);
        byte[] sig = CryptoUtils.sign(
                Attestation.signingBytes(targetNodeId, claimedScore), kp.getPrivate());
        return new Attestation(CryptoUtils.toAddress(kp.getPublic()), claimedScore, 1L, sig);
    }

    /**
     * 执行交易并出块（线程安全）。
     *
     * @param tx 交易
     * @return 新区块
     */
    public synchronized Block applyTx(Transaction tx) {
        stateMachine.apply(tx, state);
        txIndex.put(CryptoUtils.toHex(tx.getTxId()), tx);
        Block block = new Block(blocks.size(), lastHash, List.of(tx),
                System.currentTimeMillis(), new byte[0]);
        blocks.add(block);
        lastHash = block.getHash();
        return block;
    }

    /**
     * 测试/创世注资（绕过状态机，演示用）。
     */
    public synchronized void fund(byte[] address, long amount) {
        state.credit(address, amount);
    }

    public long balanceOf(byte[] address) {
        var acc = state.getAccount(address);
        return acc == null ? 0 : acc.getBalance();
    }

    /**
     * 最新区块摘要列表（按高度倒序，最多 limit 个）。
     */
    public synchronized List<BlockInfoDTO> latestBlocks(int limit) {
        List<BlockInfoDTO> out = new ArrayList<>();
        for (int i = blocks.size() - 1; i >= 0 && out.size() < limit; i--) {
            Block b = blocks.get(i);
            out.add(new BlockInfoDTO(b.getHeight(), CryptoUtils.toHex(b.getHash()),
                    CryptoUtils.toHex(b.getPrevHash()), b.getTimestamp(), b.getTransactions().size()));
        }
        return out;
    }

    /**
     * 按交易哈希查询详情。
     *
     * @param hashHex 交易哈希 hex（可带 0x 前缀）
     * @return 交易详情，找不到返回 null
     */
    public TxInfoDTO findTx(String hashHex) {
        String key = hashHex.startsWith("0x") ? hashHex.substring(2) : hashHex;
        Transaction tx = txIndex.get(key.toLowerCase());
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
        return blocks.size();
    }
}
