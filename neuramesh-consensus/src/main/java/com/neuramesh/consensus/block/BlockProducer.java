package com.neuramesh.consensus.block;

import com.neuramesh.consensus.TxPool;
import com.neuramesh.core.Block;
import com.neuramesh.core.Transaction;
import java.util.List;

/**
 * 区块打包器。
 *
 * <p>从 {@link TxPool} 取出至多 {@code maxTxs} 笔交易，构建 {@link Block}（Merkle Root 由 Block
 * 内部计算）。区块的 {@code validatorSig} 字段置空——提案人对区块哈希的签名单独随 PrePrepare 传播，
 * 从而使区块哈希独立于签名，避免循环依赖。
 */
public final class BlockProducer {

    /** 单块最大交易数。 */
    public static final int MAX_TXS_PER_BLOCK = 100;

    private final TxPool txPool;

    public BlockProducer(TxPool txPool) {
        this.txPool = java.util.Objects.requireNonNull(txPool, "txPool");
    }

    /**
     * 打包一个新区块。
     *
     * @param height    高度
     * @param prevHash  前驱区块哈希（32 字节）
     * @param timestamp 时间戳
     * @return 新区块（validatorSig 为空）
     */
    public Block produce(long height, byte[] prevHash, long timestamp) {
        List<Transaction> txs = txPool.getTransactionsForBlock(MAX_TXS_PER_BLOCK);
        return new Block(height, prevHash, txs, timestamp, new byte[0]);
    }
}
