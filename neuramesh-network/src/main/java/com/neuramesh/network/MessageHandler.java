package com.neuramesh.network;

/**
 * 消息处理器接口。
 *
 * <p>每个具体消息类型对应一个或多个 handler；{@link P2PNetwork} 收到消息后会按 typeId 查找
 * 已注册 handler 并依次调用 {@link #handle(NeuraMessage, ChannelContext)}。
 *
 * <p>实现注意：禁止在 handler 中阻塞 I/O 线程，耗时操作请丢入业务线程池。
 *
 * @param <T> 处理的具体消息类型
 */
public interface MessageHandler<T extends NeuraMessage> {

    /**
     * 处理一条消息。
     *
     * @param message 消息体
     * @param ctx     通道上下文（可用于回复或关闭连接）
     */
    void handle(T message, ChannelContext ctx);

    /**
     * 返回处理的消息类型。
     *
     * @return 消息 Class
     */
    Class<T> getType();
}