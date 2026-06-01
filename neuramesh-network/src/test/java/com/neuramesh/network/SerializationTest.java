package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.network.codec.BlockCodec;
import com.neuramesh.network.codec.TransactionCodec;
import com.neuramesh.network.messages.BlocksResponseMessage;
import com.neuramesh.network.messages.GetBlocksRequestMessage;
import com.neuramesh.network.messages.PingMessage;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SerializationTest {

    private static byte[] addr(int seed) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (seed + i);
        }
        return a;
    }

    @Test
    @DisplayName("Kryo: NeuraMessage 序列化-反序列化往返保持字段")
    void kryo_message_round_trip() {
        PingMessage ping = new PingMessage(42L);
        byte[] bytes = KryoSerialization.serialize(ping);
        assertThat(bytes[0]).isEqualTo(NeuraMessage.TYPE_PING);

        NeuraMessage back = KryoSerialization.deserialize(bytes);
        assertThat(back).isInstanceOf(PingMessage.class);
        assertThat(((PingMessage) back).getCurrentHeight()).isEqualTo(42L);
        assertThat(back.getMessageId()).isEqualTo(ping.getMessageId());
    }

    @Test
    @DisplayName("Kryo: 各消息类型 typeId 正确且可注册")
    void message_registry_types() {
        assertThat(MessageRegistry.size()).isGreaterThanOrEqualTo(6);
        assertThat(MessageRegistry.typeOf(NeuraMessage.TYPE_TX_GOSSIP))
                .isEqualTo(TransactionGossipMessage.class);
        assertThat(MessageRegistry.create(NeuraMessage.TYPE_PING)).isInstanceOf(PingMessage.class);
        assertThatThrownBy(() -> MessageRegistry.create((byte) 0x7F))
                .isInstanceOf(NetworkException.class);
    }

    @Test
    @DisplayName("Kryo: 非法字节反序列化抛出 NetworkException")
    void kryo_invalid_bytes() {
        assertThatThrownBy(() -> KryoSerialization.deserialize(new byte[] {0x01}))
                .isInstanceOf(NetworkException.class);
        assertThatThrownBy(() -> KryoSerialization.serialize(null))
                .isInstanceOf(NetworkException.class);
    }

    @Test
    @DisplayName("TransactionCodec: 签名交易往返 txId 与签名一致")
    void transaction_codec_round_trip() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        byte[] from = CryptoUtils.toAddress(kp.getPublic());
        Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, from, addr(9), 7L,
                "hello".getBytes(), 1_700_000_000_000L);
        byte[] sig = CryptoUtils.sign(tx.signingBytes(), kp.getPrivate());
        Transaction signed = tx.withSignature(sig);

        byte[] encoded = TransactionCodec.encode(signed);
        Transaction decoded = TransactionCodec.decode(encoded);

        assertThat(decoded.getTxId()).containsExactly(signed.getTxId());
        assertThat(decoded.getSignature()).containsExactly(sig);
        assertThat(decoded.getType()).isEqualTo(TxType.TOKEN_TRANSFER);
        assertThat(CryptoUtils.verify(decoded.signingBytes(), decoded.getSignature(), kp.getPublic()))
                .isTrue();
    }

    @Test
    @DisplayName("BlockCodec: 含交易区块往返哈希一致")
    void block_codec_round_trip() {
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            txs.add(Transaction.create(TxType.NODE_REGISTER, addr(1), addr(2), i,
                    new byte[] {(byte) i}, 1_700_000_000_000L + i));
        }
        Block block = new Block(3L, new byte[32], txs, 1_700_000_000_000L, new byte[] {1, 2, 3});
        byte[] encoded = BlockCodec.encode(block);
        Block decoded = BlockCodec.decode(encoded);

        assertThat(decoded.getHeight()).isEqualTo(3L);
        assertThat(decoded.getHash()).containsExactly(block.getHash());
        assertThat(decoded.getMerkleRoot()).containsExactly(block.getMerkleRoot());
        assertThat(decoded.getTransactions()).hasSize(5);
    }

    @Test
    @DisplayName("BlocksResponse 消息内多区块往返")
    void blocks_response_round_trip() {
        List<byte[]> blockBytes = new ArrayList<>();
        byte[] prev = new byte[32];
        for (int h = 0; h < 3; h++) {
            Block b = new Block(h, prev, new ArrayList<>(), 1000L + h, new byte[0]);
            blockBytes.add(BlockCodec.encode(b));
            prev = b.getHash();
        }
        BlocksResponseMessage msg = new BlocksResponseMessage(blockBytes);
        byte[] ser = KryoSerialization.serialize(msg);
        NeuraMessage back = KryoSerialization.deserialize(ser);
        assertThat(back).isInstanceOf(BlocksResponseMessage.class);
        assertThat(((BlocksResponseMessage) back).getBlockBytesList()).hasSize(3);
    }

    @Test
    @DisplayName("GetBlocksRequest 字段往返")
    void get_blocks_request_round_trip() {
        GetBlocksRequestMessage req = new GetBlocksRequestMessage(10L, 109L);
        NeuraMessage back = KryoSerialization.deserialize(KryoSerialization.serialize(req));
        assertThat(back).isInstanceOf(GetBlocksRequestMessage.class);
        GetBlocksRequestMessage r = (GetBlocksRequestMessage) back;
        assertThat(r.getStartHeight()).isEqualTo(10L);
        assertThat(r.getEndHeight()).isEqualTo(109L);
    }
}
