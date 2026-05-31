package com.neuramesh.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.MerkleTree;
import com.neuramesh.core.NeuraException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerkleTreeTest {

    private static List<byte[]> leaves(int count) {
        List<byte[]> data = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            data.add(("leaf-" + i).getBytes(StandardCharsets.UTF_8));
        }
        return data;
    }

    @Test
    @DisplayName("不同叶子数 (1/2/4/8/1000) 都能构建出 32 字节根")
    void build_with_various_leaf_counts() {
        for (int n : new int[] {1, 2, 4, 8, 1000}) {
            MerkleTree tree = new MerkleTree(leaves(n));
            assertThat(tree.getRoot()).hasSize(32);
            assertThat(tree.getLeafCount()).isEqualTo(n);
        }
    }

    @Test
    @DisplayName("根唯一性：不同输入根不同；相同输入根相同")
    void root_uniqueness_and_determinism() {
        Set<String> roots = new HashSet<>();
        Random random = new Random(0L);
        for (int run = 0; run < 50; run++) {
            int n = 1 + random.nextInt(64);
            List<byte[]> data = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                byte[] item = new byte[8];
                random.nextBytes(item);
                data.add(item);
            }
            MerkleTree t1 = new MerkleTree(data);
            MerkleTree t2 = new MerkleTree(data);
            assertThat(t1.getRoot()).containsExactly(t2.getRoot());
            roots.add(java.util.HexFormat.of().formatHex(t1.getRoot()));
        }
        assertThat(roots).hasSize(50);
    }

    @Test
    @DisplayName("Proof 验证：所有叶子的证明都能通过")
    void proof_verifies_for_all_leaves() {
        for (int n : new int[] {1, 2, 3, 4, 7, 8, 15, 100}) {
            List<byte[]> data = leaves(n);
            MerkleTree tree = new MerkleTree(data);
            byte[] root = tree.getRoot();
            for (int i = 0; i < n; i++) {
                List<MerkleTree.ProofNode> proof = tree.getProof(i);
                assertThat(MerkleTree.verifyProof(data.get(i), proof, root))
                        .as("叶子 %d (n=%d) 证明应通过", i, n)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Proof 验证：篡改数据后失败")
    void proof_fails_when_data_tampered() {
        List<byte[]> data = leaves(8);
        MerkleTree tree = new MerkleTree(data);
        List<MerkleTree.ProofNode> proof = tree.getProof(3);
        byte[] tampered = "TAMPERED".getBytes(StandardCharsets.UTF_8);
        assertThat(MerkleTree.verifyProof(tampered, proof, tree.getRoot())).isFalse();
    }

    @Test
    @DisplayName("Proof 验证：篡改根哈希后失败")
    void proof_fails_when_root_tampered() {
        List<byte[]> data = leaves(4);
        MerkleTree tree = new MerkleTree(data);
        List<MerkleTree.ProofNode> proof = tree.getProof(2);
        byte[] badRoot = tree.getRoot();
        badRoot[0] ^= 0x01;
        assertThat(MerkleTree.verifyProof(data.get(2), proof, badRoot)).isFalse();
    }

    @Test
    @DisplayName("非法参数：越界、null 抛出异常")
    void invalid_arguments() {
        MerkleTree tree = new MerkleTree(leaves(4));
        assertThatThrownBy(() -> tree.getProof(-1)).isInstanceOf(NeuraException.class);
        assertThatThrownBy(() -> tree.getProof(4)).isInstanceOf(NeuraException.class);
        assertThatThrownBy(() -> new MerkleTree(null)).isInstanceOf(NeuraException.class);
        List<byte[]> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> new MerkleTree(withNull)).isInstanceOf(NeuraException.class);
    }
}
