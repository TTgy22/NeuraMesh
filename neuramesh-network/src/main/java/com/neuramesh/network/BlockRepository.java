package com.neuramesh.network;

import com.neuramesh.core.Block;

/**
 * 区块仓库抽象，供 {@link BlockSync} 读写本地区块链。
 *
 * <p>Pause 1 提供内存实现 {@link MemoryBlockRepository}；RocksDB 持久化实现作为债务在后续 Pause 接入。
 */
public interface BlockRepository {

    /**
     * 存入区块（按高度索引）。
     *
     * @param block 区块
     */
    void put(Block block);

    /**
     * 按高度读取区块。
     *
     * @param height 高度
     * @return 区块，缺失返回 null
     */
    Block get(long height);

    /**
     * 当前最高区块高度。
     *
     * @return 最高高度；空链返回 -1
     */
    long currentHeight();
}
