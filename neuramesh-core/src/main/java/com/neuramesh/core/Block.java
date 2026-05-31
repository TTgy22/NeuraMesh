package com.neuramesh.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 不可变区块。
 *
 * <p>字段：height、prevHash、merkleRoot、timestamp、transactions、validatorSig、hash。
 * 哈希仅在调用 {@link #calculateHash()} 时按规范字段确定性计算，并在构造时缓存。
 *
 * <p>不可变性保证：所有 byte[] 字段在构造与访问时均做防御性拷贝；transactions 列表通过
 * {@link Collections#unmodifiableList(List)} 暴露，禁止外部修改。
 */
public final class Block {

    /** 创世区块的 prevHash 常量：全 0（32 字节）。 */
    public static final byte[] GENESIS_PREV_HASH = new byte[32];

    private final long height;
    private final byte[] prevHash;
    private final byte[] merkleRoot;
    private final long timestamp;
    private final List<Transaction> transactions;
    private final byte[] validatorSig;
    private final byte[] hash;

    /**
     * 全参构造器。
     *
     * @param height        区块高度（&ge; 0）
     * @param prevHash      前驱区块哈希（32 字节；创世区块为全 0）
     * @param transactions  交易列表（可为空；不可为 null，元素不可为 null）
     * @param timestamp     出块时间戳（毫秒）
     * @param validatorSig  验证者签名（可为空数组，签名前为占位）
     */
    public Block(long height, byte[] prevHash, List<Transaction> transactions,
                 long timestamp, byte[] validatorSig) {
        if (height < 0) {
            throw new NeuraException("height 不可为负: " + height);
        }
        if (prevHash == null || prevHash.length != 32) {
            throw new NeuraException("prevHash 必须为 32 字节");
        }
        Objects.requireNonNull(transactions, "transactions");
        if (validatorSig == null) {
            throw new NeuraException("validatorSig 不可为 null（可使用空数组）");
        }
        for (Transaction tx : transactions) {
            if (tx == null) {
                throw new NeuraException("交易元素不可为 null");
            }
        }
        this.height = height;
        this.prevHash = prevHash.clone();
        this.transactions = Collections.unmodifiableList(new ArrayList<>(transactions));
        this.merkleRoot = computeMerkleRoot(this.transactions);
        this.timestamp = timestamp;
        this.validatorSig = validatorSig.clone();
        this.hash = computeHash(height, this.prevHash, this.merkleRoot, timestamp, this.validatorSig);
    }

    /**
     * 构造创世区块（height=0，prevHash 为全 0）。
     *
     * @param transactions 创世交易（通常为空）
     * @param timestamp    时间戳
     * @return 创世区块
     */
    public static Block genesis(List<Transaction> transactions, long timestamp) {
        return new Block(0L, GENESIS_PREV_HASH, transactions, timestamp, new byte[0]);
    }

    /**
     * 由交易列表计算 merkleRoot；空交易列表返回全 0 哈希以保持简洁。
     */
    private static byte[] computeMerkleRoot(List<Transaction> txs) {
        if (txs.isEmpty()) {
            return new byte[32];
        }
        List<byte[]> txIds = new ArrayList<>(txs.size());
        for (Transaction tx : txs) {
            txIds.add(tx.getTxId());
        }
        return new MerkleTree(txIds).getRoot();
    }

    /**
     * 计算区块哈希：SHA-256(height || prevHash || merkleRoot || timestamp || validatorSig)。
     */
    private static byte[] computeHash(long height, byte[] prevHash, byte[] merkleRoot,
                                      long timestamp, byte[] validatorSig) {
        return CryptoUtils.sha256(
                ByteUtils.longToBytes(height),
                prevHash,
                merkleRoot,
                ByteUtils.longToBytes(timestamp),
                validatorSig);
    }

    /**
     * 返回区块哈希（防御性拷贝）。等价于构造时缓存的 hash。
     *
     * @return 32 字节哈希
     */
    public byte[] calculateHash() {
        return hash.clone();
    }

    public long getHeight() {
        return height;
    }

    public byte[] getPrevHash() {
        return prevHash.clone();
    }

    public byte[] getMerkleRoot() {
        return merkleRoot.clone();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public byte[] getValidatorSig() {
        return validatorSig.clone();
    }

    public byte[] getHash() {
        return hash.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Block other)) {
            return false;
        }
        return Arrays.equals(this.hash, other.hash);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hash);
    }

    @Override
    public String toString() {
        return "Block{height=" + height
                + ", hash=" + CryptoUtils.toHex(hash)
                + ", txCount=" + transactions.size()
                + ", timestamp=" + timestamp + '}';
    }
}
