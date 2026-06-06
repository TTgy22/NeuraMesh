package com.neuramesh.network;

import com.neuramesh.core.CryptoUtils;
import java.security.KeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 种子节点启动器（演示 / Docker 用）。
 *
 * <p>从环境变量读取配置，启动一个 {@link P2PNetwork} 服务端并连接到 bootstrap 对端，组成 P2P 网格：
 * <ul>
 *   <li>{@code NEURA_PORT}：本节点监听端口（默认 30000）；</li>
 *   <li>{@code NEURA_PEERS}：逗号分隔的 bootstrap 对端 {@code host:port}（可空，首个种子留空）；</li>
 *   <li>{@code NEURA_NODE_HEIGHT}：上报高度（默认 0）。</li>
 * </ul>
 *
 * <p>连接失败时按固定间隔重试，便于 docker-compose 各容器异步就绪。进程常驻直到收到终止信号。
 */
public final class SeedNode {

    private static final Logger LOG = LoggerFactory.getLogger(SeedNode.class);

    private SeedNode() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = envInt("NEURA_PORT", 30000);
        long height = envLong("NEURA_NODE_HEIGHT", 0L);
        String peers = System.getenv("NEURA_PEERS");

        KeyPair kp = CryptoUtils.generateKeyPair();
        NodeId nodeId = NodeId.of(CryptoUtils.toAddress(kp.getPublic()));
        PeerManager peerManager = new PeerManager();
        P2PNetwork network = new P2PNetwork(nodeId, peerManager);
        network.setHeightSupplier(() -> height);

        network.start(port);
        LOG.info("种子节点启动 nodeId={} port={}", nodeId.toHex(), port);

        Runtime.getRuntime().addShutdownHook(new Thread(network::shutdown, "seed-shutdown"));

        if (peers != null && !peers.isBlank()) {
            for (String entry : peers.split(",")) {
                connectWithRetry(network, entry.trim());
            }
        }

        // 周期性打印 Peer 数，进程常驻
        while (true) {
            Thread.sleep(15_000L);
            LOG.info("种子节点存活 nodeId={} 活跃Peer={}", nodeId.toHex().substring(0, 12),
                    peerManager.size());
        }
    }

    private static void connectWithRetry(P2PNetwork network, String hostPort) {
        if (hostPort.isEmpty()) {
            return;
        }
        int idx = hostPort.lastIndexOf(':');
        if (idx <= 0) {
            LOG.warn("非法 peer 配置（需 host:port）：{}", hostPort);
            return;
        }
        String host = hostPort.substring(0, idx);
        int port;
        try {
            port = Integer.parseInt(hostPort.substring(idx + 1));
        } catch (NumberFormatException e) {
            LOG.warn("非法 peer 端口：{}", hostPort);
            return;
        }
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                network.connectTo(host, port);
                LOG.info("已连接 bootstrap peer {}", hostPort);
                return;
            } catch (RuntimeException e) {
                LOG.info("连接 {} 失败（第 {} 次），2s 后重试", hostPort, attempt);
                try {
                    Thread.sleep(2_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        LOG.warn("放弃连接 bootstrap peer {}（超过重试上限）", hostPort);
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        try {
            return v == null || v.isBlank() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long envLong(String key, long def) {
        String v = System.getenv(key);
        try {
            return v == null || v.isBlank() ? def : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
