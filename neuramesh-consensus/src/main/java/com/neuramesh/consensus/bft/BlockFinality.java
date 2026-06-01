package com.neuramesh.consensus.bft;

import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 区块最终性记录。
 *
 * <p>记录已最终化（收到法定人数 COMMIT）的区块：高度 → 区块哈希 hex。
 * 提供查询某高度/某哈希是否已最终化，以及最高最终化高度。
 */
public final class BlockFinality {

    private final ConcurrentMap<Long, String> finalizedByHeight = new ConcurrentHashMap<>();
    private final AtomicLong highestFinalized = new AtomicLong(-1L);

    /**
     * 标记区块最终化。
     *
     * @param block 区块
     */
    public void markFinalized(Block block) {
        finalizedByHeight.put(block.getHeight(), CryptoUtils.toHex(block.getHash()));
        highestFinalized.accumulateAndGet(block.getHeight(), Math::max);
    }

    /**
     * 某高度是否已最终化。
     *
     * @param height 高度
     * @return 是否最终化
     */
    public boolean isFinalized(long height) {
        return finalizedByHeight.containsKey(height);
    }

    /**
     * 某高度最终化的区块哈希 hex。
     *
     * @param height 高度
     * @return 哈希 hex，未最终化返回 null
     */
    public String finalizedHash(long height) {
        return finalizedByHeight.get(height);
    }

    /**
     * 最高最终化高度。
     *
     * @return 高度；无则 -1
     */
    public long highestFinalizedHeight() {
        return highestFinalized.get();
    }
}
