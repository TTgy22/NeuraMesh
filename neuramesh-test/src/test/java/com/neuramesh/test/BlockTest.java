package com.neuramesh.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.MerkleTree;
import com.neuramesh.core.NeuraException;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockTest {

    private static byte[] addr(int seed) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (seed + i);
        }
        return a;
    }

    private static List<Transaction> sampleTxs(int n) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Transaction.create(TxType.TOKEN_TRANSFER, addr(1), addr(2), i,
                    new byte[] {(byte) i}, 1_700_000_000_000L + i));
        }
        return list;
    }

    @Test
    @DisplayName("创世区块：height=0，prevHash 全 0，hash 长度 32")
    void genesis_block_properties() {
        Block genesis = Block.genesis(Collections.emptyList(), 1_700_000_000_000L);
        assertThat(genesis.getHeight()).isZero();
        assertThat(genesis.getPrevHash()).containsOnly(0);
        assertThat(genesis.getHash()).hasSize(32);
        assertThat(genesis.calculateHash()).containsExactly(genesis.getHash());
    }

    @Test
    @DisplayName("哈希依赖关键字段：高度变化 -> 哈希变化")
    void hash_changes_when_height_changes() {
        List<Transaction> txs = sampleTxs(2);
        long ts = 1_700_000_000_000L;
        byte[] prev = new byte[32];
        Block b1 = new Block(1L, prev, txs, ts, new byte[0]);
        Block b2 = new Block(2L, prev, txs, ts, new byte[0]);
        assertThat(b1.getHash()).isNotEqualTo(b2.getHash());
    }

    @Test
    @DisplayName("哈希确定性：相同输入哈希一致")
    void hash_deterministic() {
        List<Transaction> txs = sampleTxs(4);
        Block a = new Block(7L, new byte[32], txs, 100L, new byte[] {1, 2});
        Block b = new Block(7L, new byte[32], txs, 100L, new byte[] {1, 2});
        assertThat(a.getHash()).containsExactly(b.getHash());
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Merkle Root 正确性：与独立计算的 MerkleTree(txIds) 一致")
    void merkle_root_matches_external_computation() {
        List<Transaction> txs = sampleTxs(5);
        Block block = new Block(1L, new byte[32], txs, 1L, new byte[0]);

        List<byte[]> ids = new ArrayList<>();
        for (Transaction tx : txs) {
            ids.add(tx.getTxId());
        }
        byte[] expectedRoot = new MerkleTree(ids).getRoot();
        assertThat(block.getMerkleRoot()).containsExactly(expectedRoot);
    }

    @Test
    @DisplayName("空交易列表：merkleRoot 为全 0")
    void empty_transactions_yields_zero_merkle_root() {
        Block block = new Block(1L, new byte[32], Collections.emptyList(), 1L, new byte[0]);
        assertThat(block.getMerkleRoot()).hasSize(32).containsOnly(0);
    }

    @Test
    @DisplayName("不可变性：getter 防御性拷贝，transactions 不可修改")
    void immutability() {
        List<Transaction> txs = new ArrayList<>(sampleTxs(2));
        Block block = new Block(1L, new byte[32], txs, 1L, new byte[] {9});

        byte[] hash = block.getHash();
        hash[0] ^= 0x55;
        assertThat(block.getHash()[0]).isNotEqualTo(hash[0]);

        assertThatThrownBy(() -> block.getTransactions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("非法参数：负高度、错长 prevHash、含 null 交易")
    void invalid_arguments() {
        assertThatThrownBy(() -> new Block(-1L, new byte[32], Collections.emptyList(), 0L, new byte[0]))
                .isInstanceOf(NeuraException.class);
        assertThatThrownBy(() -> new Block(0L, new byte[10], Collections.emptyList(), 0L, new byte[0]))
                .isInstanceOf(NeuraException.class);
        List<Transaction> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> new Block(0L, new byte[32], withNull, 0L, new byte[0]))
                .isInstanceOf(NeuraException.class);
    }
}
