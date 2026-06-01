package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.core.Block;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SyncTest {

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
    @DisplayName("新节点 D 向 A 同步区块 0-99，哈希链完整正确")
    void new_node_syncs_blocks_with_valid_chain() {
        TestNode a = newNode(1);
        TestNode d = newNode(90);
        a.start(TestNode.freePort());
        d.start(TestNode.freePort());

        // A 构造 100 个区块的链
        byte[] prev = new byte[32];
        for (long h = 0; h < 100; h++) {
            Block block = new Block(h, prev, new ArrayList<>(), 1_700_000_000_000L + h, new byte[0]);
            a.blockRepository.put(block);
            prev = block.getHash();
        }
        assertThat(a.blockRepository.currentHeight()).isEqualTo(99L);

        d.connectTo(a);
        assertThat(TestNode.waitUntil(() -> d.peerManager.getChannel(a.nodeId) != null, 10_000))
                .as("D 应完成与 A 的握手").isTrue();

        // D 请求 0-99
        d.blockSync.syncFrom(a.nodeId, 0);

        boolean synced = TestNode.waitUntil(() -> d.blockRepository.currentHeight() == 99L, 10_000);
        assertThat(synced).as("D 应同步到高度 99，实际 %d", d.blockRepository.currentHeight()).isTrue();
        assertThat(d.blockRepository.size()).isEqualTo(100);

        // 校验哈希链：每个 block.prevHash == 前一区块 hash
        for (long h = 1; h < 100; h++) {
            Block cur = d.blockRepository.get(h);
            Block prevBlock = d.blockRepository.get(h - 1);
            assertThat(Arrays.equals(cur.getPrevHash(), prevBlock.getHash()))
                    .as("区块 %d 的 prevHash 应匹配区块 %d 的 hash", h, h - 1)
                    .isTrue();
        }

        // 与 A 的同高度区块 hash 一致
        assertThat(d.blockRepository.get(99).getHash())
                .containsExactly(a.blockRepository.get(99).getHash());
    }
}
