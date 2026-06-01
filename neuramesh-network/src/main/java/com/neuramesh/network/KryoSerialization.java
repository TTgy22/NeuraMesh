package com.neuramesh.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.neuramesh.network.messages.BlocksResponseMessage;
import com.neuramesh.network.messages.GetBlocksRequestMessage;
import com.neuramesh.network.messages.HelloMessage;
import com.neuramesh.network.messages.PingMessage;
import com.neuramesh.network.messages.PongMessage;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Kryo 序列化工具。
 *
 * <p>线程安全：使用 {@link Pool} 复用 {@link Kryo} 实例（Kryo 本身非线程安全）。
 *
 * <p>帧格式：[1 byte typeId][N bytes Kryo body]。
 * 上层使用 {@code LengthFieldBasedFrameDecoder} 在 Kryo 帧外再加 4 字节长度头。
 */
public final class KryoSerialization {

    private static final Pool<Kryo> KRYO_POOL = new Pool<>(true, false, 32) {
        @Override
        protected Kryo create() {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            kryo.setReferences(true);
            // 注册业务消息类与常用容器，避免反序列化时的类查找开销
            kryo.register(byte[].class);
            // UUID 自定义序列化：避免 Kryo 反射访问 java.base 的私有字段（Java 17 JPMS 强封装会拒绝）
            kryo.register(UUID.class, new Serializer<UUID>() {
                @Override
                public void write(Kryo k, Output out, UUID uuid) {
                    out.writeLong(uuid.getMostSignificantBits());
                    out.writeLong(uuid.getLeastSignificantBits());
                }

                @Override
                public UUID read(Kryo k, Input in, Class<? extends UUID> type) {
                    return new UUID(in.readLong(), in.readLong());
                }
            });
            kryo.register(ArrayList.class);
            kryo.register(HashMap.class);
            kryo.register(LinkedHashMap.class);
            kryo.register(TransactionGossipMessage.class);
            kryo.register(GetBlocksRequestMessage.class);
            kryo.register(BlocksResponseMessage.class);
            kryo.register(PingMessage.class);
            kryo.register(PongMessage.class);
            kryo.register(HelloMessage.class);
            return kryo;
        }
    };

    private KryoSerialization() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 序列化消息为字节数组（首字节为 typeId）。
     *
     * @param message 消息对象
     * @return 序列化字节
     */
    public static byte[] serialize(NeuraMessage message) {
        if (message == null) {
            throw new NetworkException("序列化消息不可为 null");
        }
        Kryo kryo = KRYO_POOL.obtain();
        Output output = new Output(256, -1);
        try {
            output.writeByte(message.getTypeId());
            kryo.writeObject(output, message);
            output.flush();
            return output.toBytes();
        } finally {
            output.close();
            KRYO_POOL.free(kryo);
        }
    }

    /**
     * 反序列化字节为消息对象（首字节为 typeId）。
     *
     * @param bytes 序列化字节（不可为 null，不可为空）
     * @return 消息对象
     */
    public static NeuraMessage deserialize(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            throw new NetworkException("反序列化字节过短");
        }
        Kryo kryo = KRYO_POOL.obtain();
        Input input = new Input(bytes);
        try {
            byte typeId = input.readByte();
            Class<? extends NeuraMessage> clazz = MessageRegistry.typeOf(typeId);
            if (clazz == null) {
                throw new NetworkException("未知消息类型 ID: 0x" + String.format("%02X", typeId));
            }
            return kryo.readObject(input, clazz);
        } catch (NetworkException e) {
            throw e;
        } catch (Exception e) {
            throw new NetworkException("Kryo 反序列化失败", e);
        } finally {
            input.close();
            KRYO_POOL.free(kryo);
        }
    }
}