package com.neuramesh.vm;

import com.neuramesh.core.ByteUtils;
import com.neuramesh.core.CryptoUtils;

/**
 * 权重见证记录。
 *
 * <p>验证者对某节点声明的分数（{@code claimedScore}）进行签名背书。
 * 签名内容为 {@code targetNodeId || doubleToLongBits(claimedScore)}。
 *
 * @param validatorId  见证验证者的 20 字节地址
 * @param claimedScore 见证声明的分数
 * @param timestamp    见证时间戳
 * @param signature    验证者对见证内容的签名
 */
public record Attestation(byte[] validatorId, double claimedScore, long timestamp, byte[] signature) {

    public Attestation {
        if (validatorId == null || validatorId.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new IllegalArgumentException("validatorId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature 不可为 null");
        }
        validatorId = validatorId.clone();
        signature = signature.clone();
    }

    @Override
    public byte[] validatorId() {
        return validatorId.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    public String validatorIdHex() {
        return CryptoUtils.toHex(validatorId);
    }

    /**
     * 见证签名内容（targetNodeId || claimedScore 位模式）。
     *
     * @param targetNodeId 被见证节点
     * @param claimedScore 声明分数
     * @return 待签名字节
     */
    public static byte[] signingBytes(byte[] targetNodeId, double claimedScore) {
        return ByteUtils.concat(targetNodeId,
                ByteUtils.longToBytes(Double.doubleToLongBits(claimedScore)));
    }
}
