package com.neuramesh.consensus.block;

import com.neuramesh.consensus.exception.ConsensusException;
import com.neuramesh.core.Block;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 {@link ConcurrentHashMap} 的内存区块存储（线程安全）。
 */
public final class InMemoryBlockStore implements BlockStore {

    private final ConcurrentMap<Long, Block> blocks = new ConcurrentHashMap<>();
    private final AtomicLong maxHeight = new AtomicLong(-1L);

    @Override
    public void put(Block block) {
        if (block == null) {
            throw new ConsensusException("put 区块不可为 null");
        }
        blocks.put(block.getHeight(), block);
        maxHeight.accumulateAndGet(block.getHeight(), Math::max);
    }

    @Override
    public Block get(long height) {
        return blocks.get(height);
    }

    @Override
    public long currentHeight() {
        return maxHeight.get();
    }

    public int size() {
        return blocks.size();
    }
}
