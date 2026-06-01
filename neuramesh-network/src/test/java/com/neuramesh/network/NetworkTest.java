package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class NetworkTest {

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
                // 测试清理阶段忽略关闭异常
            }
        }
        nodes.clear();
    }

    @Test
    @Timeout(30)
    @DisplayName("3 节点组网：B、C 连接 A 后，A 的 Peer 数 = 2")
    void three_nodes_form_network() {
        TestNode a = newNode(1);
        TestNode b = newNode(50);
        TestNode c = newNode(100);

        a.start(TestNode.freePort());
        b.start(TestNode.freePort());
        c.start(TestNode.freePort());

        b.connectTo(a);
        c.connectTo(a);

        boolean ok = TestNode.waitUntil(() -> a.peerManager.size() == 2, 10_000);
        assertThat(ok).as("A 应在超时前发现 2 个 Peer，实际 %d", a.peerManager.size()).isTrue();
        assertThat(a.peerManager.size()).isEqualTo(2);

        // B 与 C 各自只连接了 A
        assertThat(TestNode.waitUntil(() -> b.peerManager.size() == 1, 5_000)).isTrue();
        assertThat(TestNode.waitUntil(() -> c.peerManager.size() == 1, 5_000)).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("握手后双方互相登记对端 NodeId")
    void handshake_exchanges_node_ids() {
        TestNode a = newNode(1);
        TestNode b = newNode(80);
        a.start(TestNode.freePort());
        b.start(TestNode.freePort());
        b.connectTo(a);

        assertThat(TestNode.waitUntil(() -> a.peerManager.getPeer(b.nodeId) != null, 10_000)).isTrue();
        assertThat(TestNode.waitUntil(() -> b.peerManager.getPeer(a.nodeId) != null, 10_000)).isTrue();
        assertThat(a.peerManager.getPeer(b.nodeId).getNodeId()).isEqualTo(b.nodeId);
    }
}
