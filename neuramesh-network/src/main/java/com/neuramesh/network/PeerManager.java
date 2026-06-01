package com.neuramesh.network;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 活跃 Peer 管理器。
 *
 * <p>维护：
 * <ul>
 *   <li>{@code peers}: NodeId → Peer 元数据；</li>
 *   <li>{@code channels}: NodeId → ChannelContext，用于按 NodeId 发送消息。</li>
 * </ul>
 *
 * <p>线程安全：底层 {@link ConcurrentHashMap}。
 *
 * <p>容量上限：默认 50（由 {@link #MAX_PEERS} 定义），超出时新连接将被拒绝。
 */
public final class PeerManager {

    private static final Logger LOG = LoggerFactory.getLogger(PeerManager.class);

    /** 最大 Peer 数（防止资源耗尽）。 */
    public static final int MAX_PEERS = 50;

    private final Map<NodeId, Peer> peers = new ConcurrentHashMap<>();
    private final Map<NodeId, ChannelContext> channels = new ConcurrentHashMap<>();

    /**
     * 添加 Peer 与对应通道。若已存在或超出容量，返回 false。
     *
     * @param peer    Peer 元数据
     * @param channel Peer 对应的通道
     * @return 是否成功加入
     */
    public boolean addPeer(Peer peer, ChannelContext channel) {
        if (peer == null || channel == null) {
            throw new NetworkException("addPeer 参数不可为 null");
        }
        if (peers.size() >= MAX_PEERS) {
            LOG.warn("PeerManager 已达上限 {}，拒绝 {}", MAX_PEERS, peer);
            return false;
        }
        Peer prev = peers.putIfAbsent(peer.getNodeId(), peer);
        if (prev != null) {
            LOG.debug("Peer {} 已存在，忽略重复添加", peer.getNodeId());
            return false;
        }
        channels.put(peer.getNodeId(), channel);
        LOG.info("Peer 加入: {}", peer);
        return true;
    }

    /**
     * 按 NodeId 移除 Peer 并关闭通道。
     *
     * @param id NodeId
     * @return 被移除的 Peer，若不存在返回 null
     */
    public Peer removePeer(NodeId id) {
        if (id == null) {
            return null;
        }
        Peer removed = peers.remove(id);
        ChannelContext ctx = channels.remove(id);
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignore) {
                // 通道关闭失败不应影响后续清理
            }
        }
        if (removed != null) {
            LOG.info("Peer 移除: {}", removed);
        }
        return removed;
    }

    /**
     * 当前所有 Peer。
     *
     * @return 不可变快照
     */
    public Collection<Peer> getPeers() {
        return Collections.unmodifiableCollection(peers.values());
    }

    /**
     * 按 NodeId 查询 Peer。
     *
     * @param id NodeId
     * @return Peer 或 null
     */
    public Peer getPeer(NodeId id) {
        return peers.get(id);
    }

    /**
     * 按 NodeId 查询通道。
     *
     * @param id NodeId
     * @return ChannelContext 或 null
     */
    public ChannelContext getChannel(NodeId id) {
        return channels.get(id);
    }

    /**
     * 当前 Peer 数量。
     *
     * @return 数量
     */
    public int size() {
        return peers.size();
    }

    /**
     * 广播消息到除 {@code exclude} 之外的所有 Peer。
     *
     * @param msg     消息
     * @param exclude 排除的 NodeId（可为 null，表示不排除任何节点）
     */
    public void broadcast(NeuraMessage msg, NodeId exclude) {
        if (msg == null) {
            throw new NetworkException("broadcast 消息不可为 null");
        }
        for (Map.Entry<NodeId, ChannelContext> entry : channels.entrySet()) {
            if (exclude != null && exclude.equals(entry.getKey())) {
                continue;
            }
            try {
                entry.getValue().send(msg);
            } catch (Exception e) {
                LOG.warn("向 {} 发送消息失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 关闭所有通道并清空。
     */
    public void clear() {
        for (ChannelContext ctx : channels.values()) {
            try {
                ctx.close();
            } catch (Exception ignore) {
                // 通道关闭失败不应阻断清理
            }
        }
        peers.clear();
        channels.clear();
    }
}