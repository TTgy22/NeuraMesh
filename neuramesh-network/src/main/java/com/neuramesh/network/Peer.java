package com.neuramesh.network;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 远端节点元数据。
 *
 * <p>{@code lastHeartbeat} 与 {@code currentHeight} 使用原子类型，由 {@link Heartbeat}
 * 在收到 Ping/Pong 时更新；其他字段不可变。
 */
public final class Peer {

    private final NodeId nodeId;
    private final String host;
    private final int port;
    private final AtomicLong lastHeartbeat;
    private final AtomicLong currentHeight;
    private final long weight;

    public Peer(NodeId nodeId, String host, int port, long weight) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.weight = weight;
        this.lastHeartbeat = new AtomicLong(System.currentTimeMillis());
        this.currentHeight = new AtomicLong(0L);
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat.get();
    }

    public void touchHeartbeat() {
        lastHeartbeat.set(System.currentTimeMillis());
    }

    public long getCurrentHeight() {
        return currentHeight.get();
    }

    public void setCurrentHeight(long height) {
        currentHeight.set(height);
    }

    public long getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Peer other)) {
            return false;
        }
        return nodeId.equals(other.nodeId);
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }

    @Override
    public String toString() {
        return "Peer{" + nodeId + " @ " + host + ":" + port + ", h=" + getCurrentHeight() + "}";
    }
}