package com.neuramesh.network;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.network.messages.BlocksResponseMessage;
import com.neuramesh.network.messages.GetBlocksRequestMessage;
import com.neuramesh.network.messages.HelloMessage;
import com.neuramesh.network.messages.PingMessage;
import com.neuramesh.network.messages.PongMessage;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 消息 POJO 的构造器、getter/setter 与防御性拷贝、null 处理覆盖测试。
 */
class MessagePojoTest {

    @Test
    @DisplayName("NeuraMessage 基类公共字段读写")
    void base_fields() {
        PingMessage m = new PingMessage();
        UUID id = UUID.randomUUID();
        m.setMessageId(id);
        m.setTimestamp(123L);
        m.setFromNodeId(new byte[] {1, 2, 3});
        assertThat(m.getMessageId()).isEqualTo(id);
        assertThat(m.getTimestamp()).isEqualTo(123L);
        assertThat(m.getFromNodeId()).containsExactly(1, 2, 3);

        // null fromNodeId 归一化为空数组（防御）
        m.setFromNodeId(null);
        assertThat(m.getFromNodeId()).isEmpty();
        // 默认构造的消息自带非空 messageId 与时间戳
        assertThat(new PongMessage().getMessageId()).isNotNull();
    }

    @Test
    @DisplayName("HelloMessage 字段与防御性拷贝")
    void hello_message() {
        HelloMessage h = new HelloMessage(new byte[] {9, 9}, 30001, 42L);
        assertThat(h.getTypeId()).isEqualTo(NeuraMessage.TYPE_HELLO);
        assertThat(h.getNodeId()).containsExactly(9, 9);
        assertThat(h.getListeningPort()).isEqualTo(30001);
        assertThat(h.getCurrentHeight()).isEqualTo(42L);

        byte[] leaked = h.getNodeId();
        leaked[0] = 0;
        assertThat(h.getNodeId()).containsExactly(9, 9);

        h.setNodeId(null);
        assertThat(h.getNodeId()).isEmpty();
        h.setListeningPort(40000);
        h.setCurrentHeight(7L);
        assertThat(h.getListeningPort()).isEqualTo(40000);
        assertThat(h.getCurrentHeight()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Ping/Pong 高度字段读写")
    void ping_pong() {
        PingMessage ping = new PingMessage(5L);
        assertThat(ping.getTypeId()).isEqualTo(NeuraMessage.TYPE_PING);
        assertThat(ping.getCurrentHeight()).isEqualTo(5L);
        ping.setCurrentHeight(9L);
        assertThat(ping.getCurrentHeight()).isEqualTo(9L);

        PongMessage pong = new PongMessage(11L);
        assertThat(pong.getTypeId()).isEqualTo(NeuraMessage.TYPE_PONG);
        assertThat(pong.getCurrentHeight()).isEqualTo(11L);
        pong.setCurrentHeight(13L);
        assertThat(pong.getCurrentHeight()).isEqualTo(13L);
    }

    @Test
    @DisplayName("TransactionGossipMessage 字段、防御性拷贝、null 处理")
    void tx_gossip_message() {
        TransactionGossipMessage m = new TransactionGossipMessage(new byte[] {1}, new byte[] {2, 3});
        assertThat(m.getTypeId()).isEqualTo(NeuraMessage.TYPE_TX_GOSSIP);
        assertThat(m.getTxId()).containsExactly(1);
        assertThat(m.getTxBytes()).containsExactly(2, 3);

        m.getTxBytes()[0] = 99;
        assertThat(m.getTxBytes()).containsExactly(2, 3);

        m.setTxId(null);
        m.setTxBytes(null);
        assertThat(m.getTxId()).isEmpty();
        assertThat(m.getTxBytes()).isEmpty();

        m.setTxId(new byte[] {7});
        m.setTxBytes(new byte[] {8});
        assertThat(m.getTxId()).containsExactly(7);
        assertThat(m.getTxBytes()).containsExactly(8);

        assertThat(new TransactionGossipMessage().getTxBytes()).isEmpty();
    }

    @Test
    @DisplayName("GetBlocksRequest / BlocksResponse 字段与 null 处理")
    void block_messages() {
        GetBlocksRequestMessage req = new GetBlocksRequestMessage(1L, 100L);
        assertThat(req.getTypeId()).isEqualTo(NeuraMessage.TYPE_GET_BLOCKS_REQ);
        assertThat(req.getStartHeight()).isEqualTo(1L);
        assertThat(req.getEndHeight()).isEqualTo(100L);
        req.setStartHeight(2L);
        req.setEndHeight(200L);
        assertThat(req.getStartHeight()).isEqualTo(2L);
        assertThat(req.getEndHeight()).isEqualTo(200L);

        List<byte[]> blocks = new ArrayList<>();
        blocks.add(new byte[] {1});
        BlocksResponseMessage resp = new BlocksResponseMessage(blocks);
        assertThat(resp.getTypeId()).isEqualTo(NeuraMessage.TYPE_BLOCKS_RESPONSE);
        assertThat(resp.getBlockBytesList()).hasSize(1);

        resp.setBlockBytesList(null);
        assertThat(resp.getBlockBytesList()).isEmpty();
        resp.setBlockBytesList(blocks);
        assertThat(resp.getBlockBytesList()).hasSize(1);
        assertThat(new BlocksResponseMessage(null).getBlockBytesList()).isEmpty();
    }
}
