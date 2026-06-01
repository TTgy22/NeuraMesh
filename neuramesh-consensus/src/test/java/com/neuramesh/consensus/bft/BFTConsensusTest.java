package com.neuramesh.consensus.bft;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BFTConsensusTest {

    @Test
    @Timeout(60)
    @DisplayName("4 节点正常出块：连续 10 区块全部最终化且无分叉")
    void four_nodes_finalize_ten_blocks_without_fork() {
        TestValidators tv = TestValidators.equalWeight(4);
        InMemoryConsensusCluster cluster = new InMemoryConsensusCluster(tv, 0);
        // quorum = floor(2*4/3)+1 = 3
        assertThat(tv.set.quorum()).isEqualTo(3);

        for (long h = 0; h < 10; h++) {
            cluster.runRound(h);

            // 所有 4 个节点都应在该高度最终化
            Set<String> hashesAtHeight = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                BFTConsensus node = cluster.node(i);
                assertThat(node.getFinality().isFinalized(h))
                        .as("节点 %d 应最终化高度 %d", i, h).isTrue();
                assertThat(node.getState()).isEqualTo(ConsensusState.FINALIZED);
                Block block = cluster.stores.get(i).get(h);
                assertThat(block).as("节点 %d 应存有高度 %d 的区块", i, h).isNotNull();
                hashesAtHeight.add(CryptoUtils.toHex(block.getHash()));
            }
            // 无分叉：4 个节点在该高度的区块哈希一致
            assertThat(hashesAtHeight).as("高度 %d 不应分叉", h).hasSize(1);
        }

        // 哈希链完整：每个区块 prevHash == 前一区块 hash
        for (long h = 1; h < 10; h++) {
            Block cur = cluster.stores.get(0).get(h);
            Block prev = cluster.stores.get(0).get(h - 1);
            assertThat(cur.getPrevHash()).containsExactly(prev.getHash());
        }
        assertThat(cluster.node(0).getFinality().highestFinalizedHeight()).isEqualTo(9L);
    }

    @Test
    @Timeout(60)
    @DisplayName("最终化区块写入 BlockStore，高度递增")
    void finalized_blocks_written_to_store() {
        TestValidators tv = TestValidators.equalWeight(4);
        InMemoryConsensusCluster cluster = new InMemoryConsensusCluster(tv, 0);
        for (long h = 0; h < 3; h++) {
            cluster.runRound(h);
        }
        for (int i = 0; i < 4; i++) {
            assertThat(cluster.stores.get(i).currentHeight()).isEqualTo(2L);
        }
    }
}
