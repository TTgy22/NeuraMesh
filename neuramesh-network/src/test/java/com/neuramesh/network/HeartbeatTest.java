package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class HeartbeatTest {

    private final List<TestNode> nodes = new ArrayList<>();

    private TestNode newNode(int idSeed) {
        byte[] id = new byte[NodeId.LENGTH];
        for (int i = 0; i < id.length; i++) {
            id[i] = (byte) (idSeed + i);
        }
        TestNode node = new TestNode(id);
        nodes.add(node);
        return node;
    }

    @AfterEach
    void tearDown() {
        for (TestNode n : nodes) {
            try {
                n.shutdown();
            } catch (Exception ignore) {
                // ignore
            }
        }
        nodes.clear();
    }

    @Test
    @Timeout(30)
    @DisplayName("超时未刷新心跳的 Peer 被移除")
    void stale_peer_is_evicted() throws InterruptedException {
        TestNode a = newNode(1);
        TestNode b = newNode(60);
        a.start(TestNode.freePort());
        b.start(TestNode.freePort());
        b.connectTo(a);

        assertThat(TestNode.waitUntil(() -> a.peerManager.size() == 1, 10_000)).isTrue();

        // 使用短超时的 Heartbeat，不发送 Ping，等待超过超时后驱逐
        Heartbeat fastHb = new Heartbeat(a.peerManager, () -> 0L, 1, 200L);
        Thread.sleep(400);
        int evicted = fastHb.evictStalePeers();

        assertThat(evicted).isEqualTo(1);
        assertThat(a.peerManager.size()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("Ping/Pong 交换高度")
    void ping_pong_exchanges_height() {
        TestNode a = newNode(1);
        TestNode b = newNode(70);
        a.start(TestNode.freePort());
        b.start(TestNode.freePort());

        // 预置不同高度
        for (long h = 0; h <= 4; h++) {
            a.blockRepository.put(genesisChain(a, h));
        }
        for (long h = 0; h <= 9; h++) {
            b.blockRepository.put(genesisChain(b, h));
        }
        assertThat(a.blockRepository.currentHeight()).isEqualTo(4L);
        assertThat(b.blockRepository.currentHeight()).isEqualTo(9L);

        b.connectTo(a);
        assertThat(TestNode.waitUntil(() -> a.peerManager.getPeer(b.nodeId) != null
                && b.peerManager.getPeer(a.nodeId) != null, 10_000)).isTrue();

        // A 主动 tick 一次发送 Ping（携带高度 4），B 回 Pong（携带高度 9）
        a.heartbeat.tick();

        assertThat(TestNode.waitUntil(
                () -> a.peerManager.getPeer(b.nodeId).getCurrentHeight() == 9L, 5_000))
                .as("A 记录的 B 高度应为 9").isTrue();
        assertThat(TestNode.waitUntil(
                () -> b.peerManager.getPeer(a.nodeId).getCurrentHeight() == 4L, 5_000))
                .as("B 记录的 A 高度应为 4").isTrue();
    }

    /** 构造一条简单链上指定高度的区块（prevHash 取前一区块 hash 或全 0）。 */
    private static com.neuramesh.core.Block genesisChain(TestNode node, long height) {
        byte[] prev = new byte[32];
        if (height > 0) {
            com.neuramesh.core.Block prevBlock = node.blockRepository.get(height - 1);
            if (prevBlock != null) {
                prev = prevBlock.getHash();
            }
        }
        return new com.neuramesh.core.Block(height, prev, new ArrayList<>(), 1000L + height, new byte[0]);
    }
}
