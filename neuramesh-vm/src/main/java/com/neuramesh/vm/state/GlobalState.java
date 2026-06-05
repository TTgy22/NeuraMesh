package com.neuramesh.vm.state;

import com.neuramesh.core.ByteUtils;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.MerkleTree;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 全局状态根。
 *
 * <p>管理所有 {@link AccountState}（按地址 hex）与 {@link NodeState}（按 nodeId hex），
 * 以及已注册设备指纹集合（保证注册唯一性）。
 *
 * <p>{@link #commit()} 对所有状态条目按键排序后构建 {@link MerkleTree}，返回确定性 state root。
 *
 * <p>快照/回滚：{@link #snapshot()} 返回深拷贝；{@link #restoreFrom(GlobalState)} 用快照覆盖当前状态，
 * 供 {@code StateMachine} 在交易执行失败时回滚。
 *
 * <p>并发：底层使用 {@link ConcurrentHashMap}；交易执行由状态机串行化（BFT 顺序保证）。
 */
public final class GlobalState {

    private final ConcurrentMap<String, AccountState> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NodeState> nodes = new ConcurrentHashMap<>();
    private final java.util.Set<String> fingerprints = ConcurrentHashMap.newKeySet();

    /**
     * 获取账户，不存在则创建（余额 0，nonce 0）。
     *
     * @param address 20 字节地址
     * @return 账户状态
     */
    public AccountState getOrCreateAccount(byte[] address) {
        String key = CryptoUtils.toHex(address);
        return accounts.computeIfAbsent(key, k -> new AccountState(address, 0, 0));
    }

    /**
     * 查询账户（不创建）。
     *
     * @param address 地址
     * @return 账户或 null
     */
    public AccountState getAccount(byte[] address) {
        return accounts.get(CryptoUtils.toHex(address));
    }

    /**
     * 测试/创世用：直接为某地址注资。
     *
     * @param address 地址
     * @param amount  金额
     */
    public void credit(byte[] address, long amount) {
        getOrCreateAccount(address).credit(amount);
    }

    public NodeState getNode(byte[] nodeId) {
        return nodes.get(CryptoUtils.toHex(nodeId));
    }

    public void putNode(NodeState node) {
        nodes.put(node.getNodeIdHex(), node);
    }

    public boolean isFingerprintRegistered(byte[] fingerprint) {
        return fingerprints.contains(CryptoUtils.toHex(fingerprint));
    }

    public void registerFingerprint(byte[] fingerprint) {
        fingerprints.add(CryptoUtils.toHex(fingerprint));
    }

    public int accountCount() {
        return accounts.size();
    }

    public int nodeCount() {
        return nodes.size();
    }

    /**
     * 所有账户余额之和（用于守恒校验）。
     *
     * @return 总余额
     */
    public long totalBalance() {
        long sum = 0;
        for (AccountState a : accounts.values()) {
            sum += a.getBalance();
        }
        return sum;
    }

    /**
     * 计算当前状态的 Merkle Root（确定性：按键排序）。
     *
     * @return 32 字节 state root
     */
    public byte[] commit() {
        List<byte[]> leaves = new ArrayList<>();
        Map<String, AccountState> sortedAccounts = new TreeMap<>(accounts);
        for (Map.Entry<String, AccountState> e : sortedAccounts.entrySet()) {
            AccountState a = e.getValue();
            leaves.add(ByteUtils.concat(
                    ("A:" + e.getKey()).getBytes(StandardCharsets.UTF_8),
                    ByteUtils.longToBytes(a.getBalance()),
                    ByteUtils.longToBytes(a.getNonce())));
        }
        Map<String, NodeState> sortedNodes = new TreeMap<>(nodes);
        for (Map.Entry<String, NodeState> e : sortedNodes.entrySet()) {
            NodeState n = e.getValue();
            leaves.add(ByteUtils.concat(
                    ("N:" + e.getKey()).getBytes(StandardCharsets.UTF_8),
                    ByteUtils.longToBytes(Double.doubleToLongBits(n.getTotalWeight())),
                    ByteUtils.longToBytes(n.getTotalEarned())));
        }
        return new MerkleTree(leaves).getRoot();
    }

    /**
     * 深拷贝快照。
     *
     * @return 新的 GlobalState（与当前互不影响）
     */
    public GlobalState snapshot() {
        GlobalState s = new GlobalState();
        for (Map.Entry<String, AccountState> e : accounts.entrySet()) {
            s.accounts.put(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<String, NodeState> e : nodes.entrySet()) {
            s.nodes.put(e.getKey(), e.getValue().copy());
        }
        s.fingerprints.addAll(fingerprints);
        return s;
    }

    /**
     * 用快照覆盖当前状态（回滚）。
     *
     * @param snapshot 先前的快照
     */
    public void restoreFrom(GlobalState snapshot) {
        accounts.clear();
        nodes.clear();
        fingerprints.clear();
        for (Map.Entry<String, AccountState> e : snapshot.accounts.entrySet()) {
            accounts.put(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<String, NodeState> e : snapshot.nodes.entrySet()) {
            nodes.put(e.getKey(), e.getValue().copy());
        }
        fingerprints.addAll(snapshot.fingerprints);
    }
}
