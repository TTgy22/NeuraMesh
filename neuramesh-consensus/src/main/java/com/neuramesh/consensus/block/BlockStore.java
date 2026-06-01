package com.neuramesh.consensus.block;

import com.neuramesh.core.Block;

/**
 * 区块存储抽象（consensus 模块自有，仅依赖 core）。
 *
 * <p>Pause 2 提供 {@link InMemoryBlockStore}；RocksDB 持久化适配器属应用层装配，留待后续 Pause
 * （consensus 不直接依赖 storage / network，以保持模块边界清晰）。
 */
public interface BlockStore {

    /**
     * 按高度存入区块。
     *
     * @param block 区块
     */
    void put(Block block);

    /**
     * 按高度读取。
     *
     * @param height 高度
     * @return 区块，缺失返回 null
     */
    Block get(long height);

    /**
     * 当前最高高度。
     *
     * @return 高度；空链返回 -1
     */
    long currentHeight();

    /**
     * 最新区块。
     *
     * @return 最新区块；空链返回 null
     */
    default Block getLatestBlock() {
        long h = currentHeight();
        return h < 0 ? null : get(h);
    }
}
