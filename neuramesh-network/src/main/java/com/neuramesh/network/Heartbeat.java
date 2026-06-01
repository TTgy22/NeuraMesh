package com.neuramesh.network;

import com.neuramesh.network.messages.PingMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 心跳与离线检测。
 *
 * <p>每 {@link #PING_INTERVAL_SECONDS} 秒向所有 Peer 发送 {@link PingMessage}（携带本节点高度）；
 * 同时扫描 Peer，若其 {@code lastHeartbeat} 超过 {@link #TIMEOUT_MILLIS} 未刷新，则判定离线并从
 * {@link PeerManager} 移除。
 *
 * <p>Ping/Pong 的高度交换与 {@code lastHeartbeat} 刷新由 {@link P2PNetwork} 的入站处理完成。
 */
public final class Heartbeat {

    private static final Logger LOG = LoggerFactory.getLogger(Heartbeat.class);

    /** Ping 发送间隔（秒）。 */
    public static final int PING_INTERVAL_SECONDS = 5;

    /** 心跳超时（毫秒）：超过则判定离线。 */
    public static final long TIMEOUT_MILLIS = 15_000L;

    private final PeerManager peerManager;
    private final LongSupplier heightSupplier;
    private final int pingIntervalSeconds;
    private final long timeoutMillis;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 使用默认心跳间隔（{@link #PING_INTERVAL_SECONDS}）与超时（{@link #TIMEOUT_MILLIS}）。
     *
     * @param peerManager    Peer 管理器
     * @param heightSupplier 当前高度提供者
     */
    public Heartbeat(PeerManager peerManager, LongSupplier heightSupplier) {
        this(peerManager, heightSupplier, PING_INTERVAL_SECONDS, TIMEOUT_MILLIS);
    }

    /**
     * 自定义心跳间隔与超时（便于测试）。
     *
     * @param peerManager         Peer 管理器
     * @param heightSupplier      当前高度提供者
     * @param pingIntervalSeconds Ping 间隔（秒）
     * @param timeoutMillis       离线超时（毫秒）
     */
    public Heartbeat(PeerManager peerManager, LongSupplier heightSupplier,
                     int pingIntervalSeconds, long timeoutMillis) {
        this.peerManager = java.util.Objects.requireNonNull(peerManager, "peerManager");
        this.heightSupplier = (heightSupplier == null) ? () -> 0L : heightSupplier;
        this.pingIntervalSeconds = pingIntervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "neura-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动周期心跳。
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(this::tick,
                pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
        LOG.info("Heartbeat 启动，间隔 {}s，超时 {}ms", pingIntervalSeconds, timeoutMillis);
    }

    /**
     * 执行一次心跳轮询：发送 Ping + 清理离线 Peer。可单独调用以便测试。
     */
    public void tick() {
        try {
            long height = heightSupplier.getAsLong();
            peerManager.broadcast(new PingMessage(height), null);
            evictStalePeers();
        } catch (Exception e) {
            LOG.warn("Heartbeat tick 异常: {}", e.getMessage());
        }
    }

    /**
     * 移除超时未响应的 Peer。
     *
     * @return 被移除的 Peer 数量
     */
    public int evictStalePeers() {
        long now = System.currentTimeMillis();
        List<NodeId> stale = new ArrayList<>();
        for (Peer peer : peerManager.getPeers()) {
            if (now - peer.getLastHeartbeat() > timeoutMillis) {
                stale.add(peer.getNodeId());
            }
        }
        for (NodeId id : stale) {
            peerManager.removePeer(id);
            LOG.info("心跳超时，移除离线 Peer {}", id);
        }
        return stale.size();
    }

    /**
     * 停止心跳。
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            LOG.info("Heartbeat 停止");
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
