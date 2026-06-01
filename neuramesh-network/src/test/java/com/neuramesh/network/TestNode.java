package com.neuramesh.network;

import com.neuramesh.core.Transaction;
import com.neuramesh.network.messages.BlocksResponseMessage;
import com.neuramesh.network.messages.GetBlocksRequestMessage;
import com.neuramesh.network.messages.TransactionGossipMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * 测试用节点装配：聚合 {@link P2PNetwork}、{@link PeerManager}、{@link GossipProtocol}、
 * {@link BlockSync}、{@link Heartbeat}，并注册消息处理器把网络消息接到对应业务逻辑。
 */
final class TestNode {

    final NodeId nodeId;
    final PeerManager peerManager;
    final P2PNetwork network;
    final GossipProtocol gossip;
    final MemoryBlockRepository blockRepository;
    final BlockSync blockSync;
    final Heartbeat heartbeat;

    /** 本地收到（首次）的交易：txIdHex -> Transaction。 */
    final Map<String, Transaction> receivedTx = new ConcurrentHashMap<>();

    int port = -1;

    TestNode(byte[] idBytes) {
        this.nodeId = NodeId.of(idBytes);
        this.peerManager = new PeerManager();
        this.network = new P2PNetwork(nodeId, peerManager);
        this.gossip = new GossipProtocol(peerManager);
        this.blockRepository = new MemoryBlockRepository();
        this.blockSync = new BlockSync(blockRepository, peerManager);
        this.heartbeat = new Heartbeat(peerManager, blockRepository::currentHeight, 1, 15_000L);

        gossip.setLocalConsumer(tx ->
                receivedTx.put(com.neuramesh.core.CryptoUtils.toHex(tx.getTxId()), tx));
        network.setHeightSupplier(blockRepository::currentHeight);
        registerHandlers();
    }

    private void registerHandlers() {
        network.registerHandler(new MessageHandler<TransactionGossipMessage>() {
            @Override
            public void handle(TransactionGossipMessage message, ChannelContext ctx) {
                gossip.onTransactionGossip(message, ctx.getNodeId());
            }

            @Override
            public Class<TransactionGossipMessage> getType() {
                return TransactionGossipMessage.class;
            }
        });
        network.registerHandler(new MessageHandler<GetBlocksRequestMessage>() {
            @Override
            public void handle(GetBlocksRequestMessage message, ChannelContext ctx) {
                blockSync.onGetBlocksRequest(message, ctx);
            }

            @Override
            public Class<GetBlocksRequestMessage> getType() {
                return GetBlocksRequestMessage.class;
            }
        });
        network.registerHandler(new MessageHandler<BlocksResponseMessage>() {
            @Override
            public void handle(BlocksResponseMessage message, ChannelContext ctx) {
                blockSync.onBlocksResponse(message);
            }

            @Override
            public Class<BlocksResponseMessage> getType() {
                return BlocksResponseMessage.class;
            }
        });
    }

    void start(int port) {
        this.port = port;
        network.start(port);
    }

    void connectTo(TestNode other) {
        network.connectTo("127.0.0.1", other.port);
    }

    void shutdown() {
        heartbeat.stop();
        network.shutdown();
    }

    /**
     * 申请一个空闲端口。
     *
     * @return 端口号
     */
    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法分配空闲端口", e);
        }
    }

    /**
     * 轮询直到条件成立或超时。
     *
     * @param condition 条件
     * @param timeoutMillis 超时（毫秒）
     * @return 是否在超时前满足
     */
    static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}
