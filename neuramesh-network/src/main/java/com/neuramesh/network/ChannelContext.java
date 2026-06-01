package com.neuramesh.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Netty {@link ChannelHandlerContext} 的薄包装。
 *
 * <p>对外提供与业务无关的通道操作能力，避免业务代码直接依赖 Netty API。
 *
 * <p>包含：发送消息、获取远端地址、关闭连接、保存与读取对端 {@link NodeId}（握手后由
 * {@link com.neuramesh.network.messages.HelloMessage} 注入）。
 */
public final class ChannelContext {

    private final ChannelHandlerContext nettyCtx;
    private final AtomicReference<NodeId> remoteNodeId = new AtomicReference<>();

    public ChannelContext(ChannelHandlerContext nettyCtx) {
        this.nettyCtx = Objects.requireNonNull(nettyCtx, "nettyCtx");
    }

    /**
     * 异步发送消息到对端。
     *
     * @param msg 消息体
     */
    public void send(NeuraMessage msg) {
        if (msg == null) {
            throw new NetworkException("send 消息不可为 null");
        }
        byte[] bytes = KryoSerialization.serialize(msg);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        nettyCtx.writeAndFlush(buf);
    }

    /**
     * 关闭通道（异步）。
     */
    public void close() {
        nettyCtx.close();
    }

    /**
     * 远端 socket 地址。
     *
     * @return 远端地址，断开时可能为 null
     */
    public SocketAddress getRemoteAddress() {
        return nettyCtx.channel().remoteAddress();
    }

    /**
     * 远端 IP:port 字符串（用于日志）。
     *
     * @return 形如 "127.0.0.1:30001" 或 "unknown"
     */
    public String getRemoteAddressString() {
        SocketAddress addr = getRemoteAddress();
        if (addr instanceof InetSocketAddress isa) {
            return isa.getHostString() + ":" + isa.getPort();
        }
        return addr == null ? "unknown" : addr.toString();
    }

    /**
     * 获取本通道对端的 NodeId（握手后可用）。
     *
     * @return 对端 NodeId，未握手返回 null
     */
    public NodeId getNodeId() {
        return remoteNodeId.get();
    }

    /**
     * 设置对端 NodeId（仅供握手 handler 调用）。
     *
     * @param id 对端 NodeId
     */
    public void setNodeId(NodeId id) {
        remoteNodeId.set(id);
    }

    /**
     * 暴露底层 Netty context（仅在网络包内部使用，避免业务层依赖）。
     *
     * @return Netty 上下文
     */
    ChannelHandlerContext nettyContext() {
        return nettyCtx;
    }
}