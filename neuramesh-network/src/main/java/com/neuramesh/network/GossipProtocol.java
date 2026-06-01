package com.neuramesh.network;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.network.codec.TransactionCodec;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gossip 协议：交易在网络中的扩散与去重。
 *
 * <p>收到交易后，若 txId 未出现过，则转发给除来源外的所有 Peer，并回调本地消费者
 * （通常投递到交易池）。
 *
 * <p>去重：基于 txId 十六进制的 LRU 集合，容量上限 {@link #DEDUP_CAPACITY}，超出后淘汰最久未访问项。
 * 使用 {@link LinkedHashMap}（accessOrder=true）实现 LRU，外层用 {@link Collections#synchronizedSet}
 * 保证线程安全。
 */
public final class GossipProtocol {

    private static final Logger LOG = LoggerFactory.getLogger(GossipProtocol.class);

    /** 去重集合容量上限。 */
    public static final int DEDUP_CAPACITY = 10_000;

    private final PeerManager peerManager;
    private final Set<String> seenTxIds;
    private volatile Consumer<Transaction> localConsumer = tx -> { };

    public GossipProtocol(PeerManager peerManager) {
        this.peerManager = java.util.Objects.requireNonNull(peerManager, "peerManager");
        Map<String, Boolean> lru = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > DEDUP_CAPACITY;
            }
        };
        this.seenTxIds = Collections.synchronizedSet(Collections.newSetFromMap(lru));
    }

    /**
     * 设置本地交易消费者（如交易池入队）。仅在交易首次出现时回调。
     *
     * @param consumer 消费者
     */
    public void setLocalConsumer(Consumer<Transaction> consumer) {
        if (consumer != null) {
            this.localConsumer = consumer;
        }
    }

    /**
     * 处理并扩散一笔交易。
     *
     * @param tx   交易
     * @param from 来源节点（本地发起时可为 null，表示不排除任何 Peer）
     * @return 若为首次见到该交易并已转发返回 true；重复交易返回 false
     */
    public boolean gossipTransaction(Transaction tx, NodeId from) {
        if (tx == null) {
            throw new NetworkException("gossip 交易不可为 null");
        }
        String txIdHex = CryptoUtils.toHex(tx.getTxId());
        boolean fresh = seenTxIds.add(txIdHex);
        if (!fresh) {
            LOG.debug("交易 {} 已见过，跳过转发", txIdHex);
            return false;
        }
        // 首次见到：本地消费 + 转发
        try {
            localConsumer.accept(tx);
        } catch (Exception e) {
            LOG.warn("本地交易消费失败 {}: {}", txIdHex, e.getMessage());
        }
        TransactionGossipMessage msg =
                new TransactionGossipMessage(tx.getTxId(), TransactionCodec.encode(tx));
        peerManager.broadcast(msg, from);
        LOG.debug("转发交易 {} 给 {} 个 Peer（exclude={}）",
                txIdHex, peerManager.size(), from);
        return true;
    }

    /**
     * 处理收到的交易广播消息。
     *
     * @param msg  广播消息
     * @param from 来源节点
     * @return 是否为首次见到
     */
    public boolean onTransactionGossip(TransactionGossipMessage msg, NodeId from) {
        if (msg == null) {
            throw new NetworkException("gossip 消息不可为 null");
        }
        Transaction tx = TransactionCodec.decode(msg.getTxBytes());
        return gossipTransaction(tx, from);
    }

    /**
     * 当前去重集合大小。
     *
     * @return 大小
     */
    public int seenCount() {
        return seenTxIds.size();
    }

    /**
     * 是否已见过某 txId。
     *
     * @param txId 交易 ID
     * @return 是否已见过
     */
    public boolean hasSeen(byte[] txId) {
        return seenTxIds.contains(CryptoUtils.toHex(txId));
    }
}
