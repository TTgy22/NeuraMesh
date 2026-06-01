package com.neuramesh.network;

import com.neuramesh.network.messages.BlocksResponseMessage;
import com.neuramesh.network.messages.CommitMessage;
import com.neuramesh.network.messages.GetBlocksRequestMessage;
import com.neuramesh.network.messages.HelloMessage;
import com.neuramesh.network.messages.PingMessage;
import com.neuramesh.network.messages.PongMessage;
import com.neuramesh.network.messages.PrePrepareMessage;
import com.neuramesh.network.messages.PrepareMessage;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 消息类型注册中心。
 *
 * <p>维护 typeId 到具体 {@link NeuraMessage} 子类工厂的映射，
 * 用于 {@link KryoSerialization} 在反序列化前根据 typeId 确定具体类型。
 *
 * <p>线程安全：底层 {@link ConcurrentHashMap}，{@link #register} 与 {@link #create} 均无锁。
 *
 * <p>默认在静态块中注册 P1 定义的 6 种消息类型；如需扩展可调用 {@link #register} 追加。
 */
public final class MessageRegistry {

    private static final Map<Byte, Supplier<? extends NeuraMessage>> FACTORIES = new ConcurrentHashMap<>();
    private static final Map<Byte, Class<? extends NeuraMessage>> TYPES = new ConcurrentHashMap<>();

    static {
        register(NeuraMessage.TYPE_TX_GOSSIP,       TransactionGossipMessage.class, TransactionGossipMessage::new);
        register(NeuraMessage.TYPE_GET_BLOCKS_REQ,  GetBlocksRequestMessage.class,  GetBlocksRequestMessage::new);
        register(NeuraMessage.TYPE_BLOCKS_RESPONSE, BlocksResponseMessage.class,    BlocksResponseMessage::new);
        register(NeuraMessage.TYPE_PING,            PingMessage.class,              PingMessage::new);
        register(NeuraMessage.TYPE_PONG,            PongMessage.class,              PongMessage::new);
        register(NeuraMessage.TYPE_HELLO,           HelloMessage.class,             HelloMessage::new);
        register(NeuraMessage.TYPE_PRE_PREPARE,     PrePrepareMessage.class,        PrePrepareMessage::new);
        register(NeuraMessage.TYPE_PREPARE,         PrepareMessage.class,           PrepareMessage::new);
        register(NeuraMessage.TYPE_COMMIT,          CommitMessage.class,            CommitMessage::new);
    }

    private MessageRegistry() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 注册消息类型。重复 typeId 会覆盖旧映射。
     *
     * @param typeId  类型 ID
     * @param clazz   消息类
     * @param factory 无参工厂
     * @param <T>     消息类型
     */
    public static <T extends NeuraMessage> void register(byte typeId, Class<T> clazz, Supplier<T> factory) {
        if (clazz == null || factory == null) {
            throw new NetworkException("MessageRegistry.register 参数不可为 null");
        }
        FACTORIES.put(typeId, factory);
        TYPES.put(typeId, clazz);
    }

    /**
     * 根据 typeId 创建一个空消息实例（用于 Kryo 反序列化）。
     *
     * @param typeId 类型 ID
     * @return 新建的空消息
     */
    public static NeuraMessage create(byte typeId) {
        Supplier<? extends NeuraMessage> factory = FACTORIES.get(typeId);
        if (factory == null) {
            throw new NetworkException("未知消息类型 ID: 0x" + String.format("%02X", typeId));
        }
        return factory.get();
    }

    /**
     * 查询 typeId 对应的消息类。
     *
     * @param typeId 类型 ID
     * @return 消息类，或 null 表示未注册
     */
    public static Class<? extends NeuraMessage> typeOf(byte typeId) {
        return TYPES.get(typeId);
    }

    /**
     * 当前注册的类型数量。
     *
     * @return 类型数
     */
    public static int size() {
        return FACTORIES.size();
    }
}