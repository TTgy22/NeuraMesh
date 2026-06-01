package com.neuramesh.network;

import java.util.Objects;
import java.util.UUID;

/**
 * 网络消息抽象基类。
 *
 * <p>所有点对点消息均继承自此类，统一携带：
 * <ul>
 *   <li>{@code messageId}：UUID 形式的唯一消息标识，用于幂等去重；</li>
 *   <li>{@code timestamp}：发送方本地时间戳（毫秒）；</li>
 *   <li>{@code fromNodeId}：来源节点 ID（20 字节，未签名时由握手赋值）。</li>
 * </ul>
 *
 * <p>每个具体子类必须返回唯一的 {@code typeId}（1 字节），由 {@link MessageRegistry} 统一管理。
 *
 * <p>序列化：通过 {@link KryoSerialization#serialize(NeuraMessage)} 与
 * {@link KryoSerialization#deserialize(byte[])} 完成；二者会自动包含 typeId。
 */
public abstract class NeuraMessage {

    /** 消息类型 ID 常量（1 字节，最多 256 种）。 */
    public static final byte TYPE_TX_GOSSIP        = 0x01;
    public static final byte TYPE_GET_BLOCKS_REQ   = 0x02;
    public static final byte TYPE_BLOCKS_RESPONSE  = 0x03;
    public static final byte TYPE_PING             = 0x04;
    public static final byte TYPE_PONG             = 0x05;
    public static final byte TYPE_HELLO            = 0x06;
    // BFT 共识三阶段（0x06 已被 HELLO 占用，故顺延至 0x07/0x08/0x09）
    public static final byte TYPE_PRE_PREPARE      = 0x07;
    public static final byte TYPE_PREPARE          = 0x08;
    public static final byte TYPE_COMMIT           = 0x09;

    private UUID messageId;
    private long timestamp;
    private byte[] fromNodeId;

    /** Kryo 反序列化需要的无参构造器。 */
    protected NeuraMessage() {
        this.messageId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.fromNodeId = new byte[0];
    }

    /**
     * 完整构造器。
     *
     * @param messageId  消息 UUID
     * @param timestamp  发送方时间戳（毫秒）
     * @param fromNodeId 来源 NodeId 字节
     */
    protected NeuraMessage(UUID messageId, long timestamp, byte[] fromNodeId) {
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.timestamp = timestamp;
        this.fromNodeId = (fromNodeId == null) ? new byte[0] : fromNodeId.clone();
    }

    /**
     * 子类必须返回的消息类型 ID（1 字节）。
     *
     * @return 类型 ID
     */
    public abstract byte getTypeId();

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getFromNodeId() {
        return (fromNodeId == null) ? new byte[0] : fromNodeId.clone();
    }

    public void setFromNodeId(byte[] fromNodeId) {
        this.fromNodeId = (fromNodeId == null) ? new byte[0] : fromNodeId.clone();
    }

    /**
     * 序列化为字节数组（含 typeId 头）。便捷方法，等价于 {@link KryoSerialization#serialize}。
     *
     * @return 序列化字节
     */
    public byte[] serialize() {
        return KryoSerialization.serialize(this);
    }

    /**
     * 反序列化字节为消息对象。
     *
     * @param bytes 含 typeId 头的字节
     * @return 反序列化后的消息
     */
    public static NeuraMessage deserialize(byte[] bytes) {
        return KryoSerialization.deserialize(bytes);
    }
}