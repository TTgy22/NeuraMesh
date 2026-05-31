package com.neuramesh.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * 不可变交易对象。
 *
 * <p>字段：txId、type、from、to、nonce、payload、signature、timestamp。
 * 其中 {@code txId = SHA-256(序列化内容)}，序列化内容包含除 {@code txId} 与 {@code signature} 之外的所有规范字段，
 * 以确保 txId 不依赖签名（签名后 txId 不变）。
 */
public final class Transaction {

    /** 全 0 的 32 字节常量（用于占位）。 */
    public static final byte[] ZERO_HASH_32 = new byte[32];

    private final byte[] txId;
    private final TxType type;
    private final byte[] from;
    private final byte[] to;
    private final long nonce;
    private final byte[] payload;
    private final byte[] signature;
    private final long timestamp;

    /**
     * 全参构造器（内部使用，外部应使用 {@link #create} / {@link #withSignature}）。
     */
    private Transaction(byte[] txId, TxType type, byte[] from, byte[] to,
                        long nonce, byte[] payload, byte[] signature, long timestamp) {
        this.txId = txId.clone();
        this.type = type;
        this.from = from.clone();
        this.to = to.clone();
        this.nonce = nonce;
        this.payload = payload.clone();
        this.signature = signature.clone();
        this.timestamp = timestamp;
    }

    /**
     * 创建未签名的交易（signature 为空数组）。
     *
     * @param type      交易类型
     * @param from      发起方地址（20 字节）
     * @param to        接收方地址（20 字节，可与 from 相同）
     * @param nonce     发起方账户 nonce
     * @param payload   业务负载（按交易类型自描述，本 Pause 不解析）
     * @param timestamp 时间戳（毫秒）
     * @return 未签名交易
     */
    public static Transaction create(TxType type, byte[] from, byte[] to,
                                     long nonce, byte[] payload, long timestamp) {
        Objects.requireNonNull(type, "type");
        validateAddress(from, "from");
        validateAddress(to, "to");
        if (payload == null) {
            throw new NeuraException("payload 不可为 null（可使用空数组）");
        }
        byte[] sig = new byte[0];
        byte[] id = computeTxId(type, from, to, nonce, payload, timestamp);
        return new Transaction(id, type, from, to, nonce, payload, sig, timestamp);
    }

    /**
     * 在已有交易上附加签名，返回新交易（不可变）。
     *
     * @param signature DER 编码的 ECDSA 签名（不可为 null）
     * @return 带签名的新交易实例
     */
    public Transaction withSignature(byte[] signature) {
        if (signature == null) {
            throw new NeuraException("signature 不可为 null");
        }
        return new Transaction(this.txId, this.type, this.from, this.to,
                this.nonce, this.payload, signature, this.timestamp);
    }

    /**
     * 计算 txId。
     *
     * @return 32 字节 txId
     */
    private static byte[] computeTxId(TxType type, byte[] from, byte[] to,
                                      long nonce, byte[] payload, long timestamp) {
        byte[] typeBytes = ByteUtils.intToBytes(type.ordinal());
        byte[] nonceBytes = ByteUtils.longToBytes(nonce);
        byte[] tsBytes = ByteUtils.longToBytes(timestamp);
        byte[] payloadLen = ByteUtils.intToBytes(payload.length);
        return CryptoUtils.sha256(typeBytes, from, to, nonceBytes, payloadLen, payload, tsBytes);
    }

    /**
     * 返回签名所需的“canonical 字节”（不含 signature 本身）。
     *
     * @return canonical 字节
     */
    public byte[] signingBytes() {
        byte[] typeBytes = ByteUtils.intToBytes(type.ordinal());
        byte[] nonceBytes = ByteUtils.longToBytes(nonce);
        byte[] tsBytes = ByteUtils.longToBytes(timestamp);
        byte[] payloadLen = ByteUtils.intToBytes(payload.length);
        return ByteUtils.concat(typeBytes, from, to, nonceBytes, payloadLen, payload, tsBytes);
    }

    private static void validateAddress(byte[] addr, String name) {
        if (addr == null) {
            throw new NeuraException(name + " 地址不可为 null");
        }
        if (addr.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new NeuraException(name + " 地址长度需为 " + CryptoUtils.ADDRESS_LENGTH
                    + "，实际 " + addr.length);
        }
    }

    public byte[] getTxId() {
        return txId.clone();
    }

    public TxType getType() {
        return type;
    }

    public byte[] getFrom() {
        return from.clone();
    }

    public byte[] getTo() {
        return to.clone();
    }

    public long getNonce() {
        return nonce;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
            return false;
        }
        return Arrays.equals(this.txId, other.txId)
                && Arrays.equals(this.signature, other.signature);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(txId);
    }

    @Override
    public String toString() {
        return "Transaction{txId=" + CryptoUtils.toHex(txId)
                + ", type=" + type
                + ", nonce=" + nonce
                + ", timestamp=" + timestamp + '}';
    }
}
