package com.neuramesh.network;

import com.neuramesh.core.Block;
import com.neuramesh.network.codec.BlockCodec;
import com.neuramesh.network.messages.BlocksResponseMessage;
import com.neuramesh.network.messages.GetBlocksRequestMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 区块同步。
 *
 * <p>请求方：调用 {@link #syncFrom(NodeId, long)} 向某 Peer 发送
 * {@link GetBlocksRequestMessage}，请求 {@code [startHeight, startHeight + BATCH_SIZE - 1]} 区间。
 *
 * <p>响应方：收到请求后由 {@link #onGetBlocksRequest} 从 {@link BlockRepository} 读取区块并回复
 * {@link BlocksResponseMessage}。
 *
 * <p>接收方：收到响应后由 {@link #onBlocksResponse} 校验哈希链（每个 block.prevHash 必须等于前一区块
 * 的 hash），校验通过则存入仓库。
 *
 * <p>债务：Pause 1 仅校验哈希链连续性，不校验区块签名 / Merkle Root（留待 P2）。
 */
public final class BlockSync {

    private static final Logger LOG = LoggerFactory.getLogger(BlockSync.class);

    /** 单次同步批量大小。 */
    public static final int BATCH_SIZE = 100;

    private final BlockRepository repository;
    private final PeerManager peerManager;

    public BlockSync(BlockRepository repository, PeerManager peerManager) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.peerManager = java.util.Objects.requireNonNull(peerManager, "peerManager");
    }

    /**
     * 向指定 Peer 请求从 startHeight 开始的一批区块。
     *
     * @param peerId      目标 Peer
     * @param startHeight 起始高度
     */
    public void syncFrom(NodeId peerId, long startHeight) {
        if (peerId == null) {
            throw new NetworkException("syncFrom peerId 不可为 null");
        }
        ChannelContext ctx = peerManager.getChannel(peerId);
        if (ctx == null) {
            throw new NetworkException("Peer 未连接: " + peerId);
        }
        long endHeight = startHeight + BATCH_SIZE - 1;
        ctx.send(new GetBlocksRequestMessage(startHeight, endHeight));
        LOG.debug("向 {} 请求区块 [{}, {}]", peerId, startHeight, endHeight);
    }

    /**
     * 响应方：处理区块请求，从本地仓库读取区块并回复。
     *
     * @param req 请求消息
     * @param ctx 通道上下文
     */
    public void onGetBlocksRequest(GetBlocksRequestMessage req, ChannelContext ctx) {
        if (req == null || ctx == null) {
            throw new NetworkException("onGetBlocksRequest 参数不可为 null");
        }
        long start = Math.max(0, req.getStartHeight());
        long end = req.getEndHeight();
        // 限制单次返回数量，防止恶意请求
        end = Math.min(end, start + BATCH_SIZE - 1);

        List<byte[]> payload = new ArrayList<>();
        for (long h = start; h <= end; h++) {
            Block block = repository.get(h);
            if (block == null) {
                break;
            }
            payload.add(BlockCodec.encode(block));
        }
        ctx.send(new BlocksResponseMessage(payload));
        LOG.debug("响应区块请求 [{}, {}]，实际返回 {} 个", start, end, payload.size());
    }

    /**
     * 接收方：处理区块响应，校验哈希链后存入仓库。
     *
     * @param resp 响应消息
     * @return 成功导入的区块数量
     */
    public int onBlocksResponse(BlocksResponseMessage resp) {
        if (resp == null) {
            throw new NetworkException("onBlocksResponse 不可为 null");
        }
        List<byte[]> list = resp.getBlockBytesList();
        if (list.isEmpty()) {
            return 0;
        }
        List<Block> decoded = new ArrayList<>(list.size());
        for (byte[] bytes : list) {
            decoded.add(BlockCodec.decode(bytes));
        }
        // 校验哈希链：相邻区块 prevHash 必须等于前一区块 hash
        int imported = 0;
        for (int i = 0; i < decoded.size(); i++) {
            Block current = decoded.get(i);
            if (i > 0) {
                Block prev = decoded.get(i - 1);
                if (!Arrays.equals(current.getPrevHash(), prev.getHash())) {
                    LOG.warn("区块 {} 哈希链断裂，停止导入", current.getHeight());
                    break;
                }
            } else {
                // 首个区块：若本地存在前驱，校验衔接
                Block localPrev = repository.get(current.getHeight() - 1);
                if (localPrev != null
                        && !Arrays.equals(current.getPrevHash(), localPrev.getHash())) {
                    LOG.warn("区块 {} 与本地前驱不衔接，拒绝导入", current.getHeight());
                    break;
                }
            }
            repository.put(current);
            imported++;
        }
        LOG.debug("导入区块 {} 个，当前高度 {}", imported, repository.currentHeight());
        return imported;
    }

    public BlockRepository getRepository() {
        return repository;
    }
}
