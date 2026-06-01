package com.neuramesh.network;

import com.neuramesh.network.messages.HelloMessage;
import com.neuramesh.network.messages.PingMessage;
import com.neuramesh.network.messages.PongMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Netty 的 P2P 网络节点。
 *
 * <p>同时充当服务端（{@link ServerBootstrap} 监听端口、接受入站连接）与客户端
 * （{@link Bootstrap} 主动连接种子节点）。所有 I/O 异步执行，业务处理派发到独立线程池
 * （{@link #businessPool}），禁止阻塞 Netty I/O 线程。
 *
 * <p>帧协议：出站经 {@link LengthFieldPrepender}(4) 加 4 字节长度头；入站经
 * {@link LengthFieldBasedFrameDecoder} 按长度头拆帧，再交由 Kryo 反序列化。
 *
 * <p>握手：连接建立后双方互发 {@link HelloMessage}，交换 NodeId / 监听端口 / 当前高度，
 * 完成后将对端登记到 {@link PeerManager}。Ping 自动回 Pong。
 */
public final class P2PNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(P2PNetwork.class);

    /** 最大帧长度：16 MB（足够容纳一批区块）。 */
    public static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    private final NodeId localNodeId;
    private final PeerManager peerManager;
    private final ExecutorService businessPool;
    private final Map<Byte, MessageHandler<? extends NeuraMessage>> handlers = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int listeningPort = -1;
    private volatile LongSupplier heightSupplier = () -> 0L;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventLoopGroup clientGroup;
    private Channel serverChannel;

    /**
     * @param localNodeId 本节点 ID
     * @param peerManager Peer 管理器
     */
    public P2PNetwork(NodeId localNodeId, PeerManager peerManager) {
        this.localNodeId = java.util.Objects.requireNonNull(localNodeId, "localNodeId");
        this.peerManager = java.util.Objects.requireNonNull(peerManager, "peerManager");
        this.businessPool = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "neura-net-biz");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * 注册业务消息处理器。
     *
     * @param handler 处理器
     * @param <T>     消息类型
     */
    public <T extends NeuraMessage> void registerHandler(MessageHandler<T> handler) {
        if (handler == null) {
            throw new NetworkException("registerHandler 不可为 null");
        }
        byte typeId = resolveTypeId(handler.getType());
        handlers.put(typeId, handler);
        LOG.debug("注册消息处理器: type=0x{} -> {}",
                String.format("%02X", typeId), handler.getClass().getSimpleName());
    }

    private byte resolveTypeId(Class<? extends NeuraMessage> clazz) {
        try {
            NeuraMessage probe = clazz.getDeclaredConstructor().newInstance();
            return probe.getTypeId();
        } catch (Exception e) {
            throw new NetworkException("无法解析消息类型 ID: " + clazz.getName(), e);
        }
    }

    /**
     * 设置当前高度提供者（用于 Hello/Ping 携带高度）。
     *
     * @param supplier 高度提供者
     */
    public void setHeightSupplier(LongSupplier supplier) {
        if (supplier != null) {
            this.heightSupplier = supplier;
        }
    }

    /**
     * 启动服务端监听。
     *
     * @param port 监听端口
     */
    public void start(int port) {
        if (!running.compareAndSet(false, true)) {
            throw new NetworkException("P2PNetwork 已启动");
        }
        this.listeningPort = port;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        this.clientGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            configurePipeline(ch, false);
                        }
                    });
            this.serverChannel = b.bind(port).sync().channel();
            LOG.info("P2PNetwork 启动监听 {}（nodeId={}）", port, localNodeId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            throw new NetworkException("P2PNetwork 启动被中断", e);
        } catch (Exception e) {
            running.set(false);
            throw new NetworkException("P2PNetwork 启动失败 port=" + port, e);
        }
    }

    /**
     * 主动连接到指定节点（异步连接，方法内等待 TCP 建链完成，但不阻塞后续 I/O）。
     *
     * @param host 目标主机
     * @param port 目标端口
     * @return 建链成功返回对应 Channel，失败抛出 {@link NetworkException}
     */
    public Channel connectTo(String host, int port) {
        if (!running.get()) {
            throw new NetworkException("P2PNetwork 未启动，无法连接");
        }
        try {
            Bootstrap b = new Bootstrap();
            b.group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            configurePipeline(ch, true);
                        }
                    });
            ChannelFuture f = b.connect(host, port);
            if (!f.await(10, TimeUnit.SECONDS)) {
                throw new NetworkException("连接超时 " + host + ":" + port);
            }
            if (!f.isSuccess()) {
                throw new NetworkException("连接失败 " + host + ":" + port, f.cause());
            }
            LOG.info("已连接到 {}:{}", host, port);
            return f.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("连接被中断", e);
        }
    }

    /**
     * 优雅关闭：先向所有 Peer 广播离开（Pong 高度 -1 作为离线标记），再释放 EventLoopGroup。
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOG.info("P2PNetwork 关闭中（nodeId={}）", localNodeId);
        try {
            peerManager.clear();
        } catch (Exception ignore) {
            // 清理失败不影响后续关闭
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        shutdownGroup(bossGroup);
        shutdownGroup(workerGroup);
        shutdownGroup(clientGroup);
        businessPool.shutdownNow();
    }

    private void shutdownGroup(EventLoopGroup group) {
        if (group != null) {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    public NodeId getLocalNodeId() {
        return localNodeId;
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void configurePipeline(SocketChannel ch, boolean clientSide) {
        ch.pipeline()
                .addLast("frameDecoder",
                        new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4))
                .addLast("frameEncoder", new LengthFieldPrepender(4))
                .addLast("neura", new InboundHandler(clientSide));
    }

    /**
     * 入站消息处理器（每个连接一个实例，持有该连接的 {@link ChannelContext}）。
     */
    private final class InboundHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final boolean clientSide;
        private ChannelContext context;

        InboundHandler(boolean clientSide) {
            this.clientSide = clientSide;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            this.context = new ChannelContext(ctx);
            // 双方建链后均主动发送 Hello 完成握手
            HelloMessage hello = new HelloMessage(
                    localNodeId.toBytes(), listeningPort, heightSupplier.getAsLong());
            context.send(hello);
            LOG.debug("发送 Hello 到 {}（client={}）", context.getRemoteAddressString(), clientSide);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            final NeuraMessage message;
            try {
                message = KryoSerialization.deserialize(bytes);
            } catch (Exception e) {
                LOG.warn("消息反序列化失败，来自 {}: {}",
                        context.getRemoteAddressString(), e.getMessage());
                return;
            }
            // 反序列化在 I/O 线程，分发到业务线程池处理
            businessPool.submit(() -> dispatch(message));
        }

        private void dispatch(NeuraMessage message) {
            try {
                byte typeId = message.getTypeId();
                switch (typeId) {
                    case NeuraMessage.TYPE_HELLO -> handleHello((HelloMessage) message);
                    case NeuraMessage.TYPE_PING -> handlePing((PingMessage) message);
                    case NeuraMessage.TYPE_PONG -> handlePong((PongMessage) message);
                    default -> invokeHandler(typeId, message);
                }
            } catch (Exception e) {
                LOG.warn("处理消息异常 type=0x{}: {}",
                        String.format("%02X", message.getTypeId()), e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        private <T extends NeuraMessage> void invokeHandler(byte typeId, NeuraMessage message) {
            MessageHandler<T> handler = (MessageHandler<T>) handlers.get(typeId);
            if (handler != null) {
                handler.handle((T) message, context);
            } else {
                LOG.debug("无处理器，丢弃消息 type=0x{}", String.format("%02X", typeId));
            }
        }

        private void handleHello(HelloMessage hello) {
            NodeId remote = NodeId.of(hello.getNodeId());
            context.setNodeId(remote);
            String host = "unknown";
            int port = hello.getListeningPort();
            if (context.getRemoteAddress() instanceof java.net.InetSocketAddress isa) {
                host = isa.getHostString();
            }
            Peer peer = new Peer(remote, host, port, 0L);
            peer.setCurrentHeight(hello.getCurrentHeight());
            boolean added = peerManager.addPeer(peer, context);
            if (added) {
                LOG.info("握手完成，登记 Peer {}（client={}）", remote, clientSide);
            }
            // 同时调用外部注册的 Hello handler（若有），供上层做区块同步触发等
            invokeHandler(NeuraMessage.TYPE_HELLO, hello);
        }

        private void handlePing(PingMessage ping) {
            NodeId remote = context.getNodeId();
            if (remote != null) {
                Peer peer = peerManager.getPeer(remote);
                if (peer != null) {
                    peer.touchHeartbeat();
                    peer.setCurrentHeight(ping.getCurrentHeight());
                }
            }
            context.send(new PongMessage(heightSupplier.getAsLong()));
        }

        private void handlePong(PongMessage pong) {
            NodeId remote = context.getNodeId();
            if (remote != null) {
                Peer peer = peerManager.getPeer(remote);
                if (peer != null) {
                    peer.touchHeartbeat();
                    peer.setCurrentHeight(pong.getCurrentHeight());
                }
            }
            invokeHandler(NeuraMessage.TYPE_PONG, pong);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            NodeId remote = (context != null) ? context.getNodeId() : null;
            if (remote != null) {
                peerManager.removePeer(remote);
                LOG.info("连接断开，移除 Peer {}", remote);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warn("通道异常 {}: {}",
                    (context != null ? context.getRemoteAddressString() : "?"), cause.getMessage());
            ctx.close();
        }
    }
}
