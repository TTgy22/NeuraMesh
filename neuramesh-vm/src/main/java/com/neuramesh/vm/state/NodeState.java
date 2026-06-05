package com.neuramesh.vm.state;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.exception.VMException;

/**
 * 节点状态。
 *
 * <p>权重公式（硬编码）：
 * {@code totalWeight = hardwareScore*0.3 + qualityScore*0.4 + uptimeScore*0.2 + bandwidthScore*0.1}。
 */
public final class NodeState {

    public static final double W_HARDWARE = 0.3;
    public static final double W_QUALITY = 0.4;
    public static final double W_UPTIME = 0.2;
    public static final double W_BANDWIDTH = 0.1;

    private final byte[] nodeId;
    private final byte[] fingerprint;
    private double hardwareScore;
    private double qualityScore;
    private double uptimeScore;
    private double bandwidthScore;
    private double totalWeight;
    private long onlineSeconds;
    private long totalEarned;

    public NodeState(byte[] nodeId, byte[] fingerprint) {
        if (nodeId == null || nodeId.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD,
                    "nodeId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        if (fingerprint == null || fingerprint.length == 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "设备指纹不可为空");
        }
        this.nodeId = nodeId.clone();
        this.fingerprint = fingerprint.clone();
        this.totalWeight = 0.0;
    }

    /** 深拷贝（快照）。 */
    public NodeState copy() {
        NodeState c = new NodeState(nodeId, fingerprint);
        c.hardwareScore = hardwareScore;
        c.qualityScore = qualityScore;
        c.uptimeScore = uptimeScore;
        c.bandwidthScore = bandwidthScore;
        c.totalWeight = totalWeight;
        c.onlineSeconds = onlineSeconds;
        c.totalEarned = totalEarned;
        return c;
    }

    /**
     * 设置四项分数并按公式重算 totalWeight。
     */
    public void setScores(double hardware, double quality, double uptime, double bandwidth) {
        this.hardwareScore = hardware;
        this.qualityScore = quality;
        this.uptimeScore = uptime;
        this.bandwidthScore = bandwidth;
        recomputeWeight();
    }

    private void recomputeWeight() {
        this.totalWeight = hardwareScore * W_HARDWARE
                + qualityScore * W_QUALITY
                + uptimeScore * W_UPTIME
                + bandwidthScore * W_BANDWIDTH;
    }

    /**
     * 对偏差见证者的惩罚：质量分按比例下调并重算权重。
     *
     * @param factor 惩罚系数（0..1）
     */
    public void penalizeQuality(double factor) {
        this.qualityScore *= factor;
        recomputeWeight();
    }

    public void addEarned(long amount) {
        this.totalEarned += amount;
    }

    public byte[] getNodeId() {
        return nodeId.clone();
    }

    public String getNodeIdHex() {
        return CryptoUtils.toHex(nodeId);
    }

    public byte[] getFingerprint() {
        return fingerprint.clone();
    }

    public double getHardwareScore() {
        return hardwareScore;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public double getUptimeScore() {
        return uptimeScore;
    }

    public double getBandwidthScore() {
        return bandwidthScore;
    }

    public double getTotalWeight() {
        return totalWeight;
    }

    public long getOnlineSeconds() {
        return onlineSeconds;
    }

    public void setOnlineSeconds(long onlineSeconds) {
        this.onlineSeconds = onlineSeconds;
    }

    public long getTotalEarned() {
        return totalEarned;
    }
}
