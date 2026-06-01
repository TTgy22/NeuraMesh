package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GossipTest {

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

    private static byte[] addr(int seed) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (seed + i);
        }
        return a;
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
    @DisplayName("A 广播交易，B 与 C 收到相同 txId")
    void gossip_reaches_all_peers() {
        TestNode a = newNode(1);
        TestNode b = newNode(50);
        TestNode c = newNode(100);
        a.start(TestNode.freePort());
        b.start(TestNode.freePort());
        c.start(TestNode.freePort());
        b.connectTo(a);
        c.connectTo(a);

        // 等待 A 与两个 Peer 完成握手
        assertThat(TestNode.waitUntil(() -> a.peerManager.size() == 2, 10_000)).isTrue();

        Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, addr(1), addr(2), 1L,
                "gossip-payload".getBytes(), 1_700_000_000_000L);
        String txHex = CryptoUtils.toHex(tx.getTxId());

        // A 本地发起 gossip（from=null 表示广播给所有 Peer）
        boolean fresh = a.gossip.gossipTransaction(tx, null);
        assertThat(fresh).isTrue();

        assertThat(TestNode.waitUntil(() -> b.receivedTx.containsKey(txHex), 5_000))
                .as("B 应收到交易 %s", txHex).isTrue();
        assertThat(TestNode.waitUntil(() -> c.receivedTx.containsKey(txHex), 5_000))
                .as("C 应收到交易 %s", txHex).isTrue();

        assertThat(b.receivedTx.get(txHex).getTxId()).containsExactly(tx.getTxId());
        assertThat(c.receivedTx.get(txHex).getTxId()).containsExactly(tx.getTxId());
    }

    @Test
    @Timeout(30)
    @DisplayName("去重：重复交易不再被本地消费/转发")
    void duplicate_transaction_is_deduplicated() {
        TestNode b = newNode(50);
        Transaction tx = Transaction.create(TxType.NODE_REGISTER, addr(1), addr(2), 5L,
                new byte[] {7}, 1_700_000_000_000L);
        TransactionGossipMessage msg =
                new TransactionGossipMessage(tx.getTxId(),
                        com.neuramesh.network.codec.TransactionCodec.encode(tx));

        boolean first = b.gossip.onTransactionGossip(msg, null);
        boolean second = b.gossip.onTransactionGossip(msg, null);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(b.gossip.hasSeen(tx.getTxId())).isTrue();
        assertThat(b.receivedTx).hasSize(1);
    }
}
