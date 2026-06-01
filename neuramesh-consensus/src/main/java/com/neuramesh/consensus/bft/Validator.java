package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.exception.ConsensusException;
import com.neuramesh.core.CryptoUtils;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

/**
 * 验证者。
 *
 * <p>节点身份用 20 字节地址表示（{@code = CryptoUtils.toAddress(publicKey)}），
 * 以保持 consensus 模块仅依赖 core（不依赖 network 的 NodeId）。
 */
public final class Validator {

    private final byte[] nodeId;
    private final PublicKey publicKey;
    private final long weight;
    private final long stake;

    /**
     * @param nodeId    20 字节节点地址
     * @param publicKey 验证者公钥（用于验签投票/提案）
     * @param weight    出块权重（提案人加权轮询）
     * @param stake     质押量（P3 经济模型用，当前仅存储）
     */
    public Validator(byte[] nodeId, PublicKey publicKey, long weight, long stake) {
        if (nodeId == null || nodeId.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new ConsensusException("validator nodeId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        this.nodeId = nodeId.clone();
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        if (weight <= 0) {
            throw new ConsensusException("validator weight 必须为正: " + weight);
        }
        this.weight = weight;
        this.stake = stake;
    }

    public byte[] getNodeId() {
        return nodeId.clone();
    }

    /**
     * 节点 ID 的十六进制（map 键 / 日志用）。
     *
     * @return hex 字符串
     */
    public String getNodeIdHex() {
        return CryptoUtils.toHex(nodeId);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public long getWeight() {
        return weight;
    }

    public long getStake() {
        return stake;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Validator other)) {
            return false;
        }
        return Arrays.equals(nodeId, other.nodeId);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nodeId);
    }

    @Override
    public String toString() {
        return "Validator{" + getNodeIdHex() + ", weight=" + weight + ", stake=" + stake + '}';
    }
}
