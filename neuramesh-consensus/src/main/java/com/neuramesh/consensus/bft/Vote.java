package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.exception.ConsensusException;
import com.neuramesh.core.ByteUtils;
import com.neuramesh.core.CryptoUtils;
import java.util.Arrays;

/**
 * 投票消息（PREPARE / COMMIT）。
 *
 * <p>签名内容为 {@code typeOrdinal(4字节) || blockHash}，由验证者私钥签名；
 * {@link #verify(ValidatorSet)} 用验证者公钥验签。
 */
public final class Vote {

    private final VoteType type;
    private final byte[] blockHash;
    private final byte[] validatorId;
    private final byte[] signature;

    public Vote(VoteType type, byte[] blockHash, byte[] validatorId, byte[] signature) {
        this.type = java.util.Objects.requireNonNull(type, "type");
        if (blockHash == null || blockHash.length == 0) {
            throw new ConsensusException("blockHash 不可为空");
        }
        if (validatorId == null || validatorId.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new ConsensusException("validatorId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        this.blockHash = blockHash.clone();
        this.validatorId = validatorId.clone();
        this.signature = (signature == null) ? new byte[0] : signature.clone();
    }

    /**
     * 计算投票的签名内容（type || blockHash）。
     *
     * @param type      投票类型
     * @param blockHash 区块哈希
     * @return 待签名字节
     */
    public static byte[] signingBytes(VoteType type, byte[] blockHash) {
        return ByteUtils.concat(ByteUtils.intToBytes(type.ordinal()), blockHash);
    }

    /**
     * 用验证者集中对应公钥验签。
     *
     * @param validators 验证者集
     * @return 验签是否通过（且投票者确为验证者）
     */
    public boolean verify(ValidatorSet validators) {
        if (validators == null) {
            return false;
        }
        Validator v = validators.getByNodeId(validatorId);
        if (v == null) {
            return false;
        }
        if (signature.length == 0) {
            return false;
        }
        return CryptoUtils.verify(signingBytes(type, blockHash), signature, v.getPublicKey());
    }

    public VoteType getType() {
        return type;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public String getBlockHashHex() {
        return CryptoUtils.toHex(blockHash);
    }

    public byte[] getValidatorId() {
        return validatorId.clone();
    }

    public String getValidatorIdHex() {
        return CryptoUtils.toHex(validatorId);
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vote other)) {
            return false;
        }
        return type == other.type
                && Arrays.equals(blockHash, other.blockHash)
                && Arrays.equals(validatorId, other.validatorId);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Arrays.hashCode(blockHash);
        result = 31 * result + Arrays.hashCode(validatorId);
        return result;
    }
}
