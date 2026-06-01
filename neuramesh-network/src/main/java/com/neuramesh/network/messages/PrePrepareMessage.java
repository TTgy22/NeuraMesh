package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * PrePrepare 线路消息（PBFT 第一阶段，typeId=0x07）。
 *
 * <p>承载提案区块的序列化字节（由 BlockCodec 编码）、提案人节点地址与提案人签名。
 * 仅携带原语字段，便于 Kryo 序列化，与 consensus 层的 PrePrepare 对象互相转换由装配层完成。
 */
public class PrePrepareMessage extends NeuraMessage {

    private long height;
    private byte[] blockBytes;
    private byte[] proposerId;
    private byte[] proposerSignature;

    public PrePrepareMessage() {
        super();
    }

    public PrePrepareMessage(long height, byte[] blockBytes, byte[] proposerId,
                             byte[] proposerSignature) {
        super();
        this.height = height;
        this.blockBytes = copy(blockBytes);
        this.proposerId = copy(proposerId);
        this.proposerSignature = copy(proposerSignature);
    }

    private static byte[] copy(byte[] b) {
        return (b == null) ? new byte[0] : b.clone();
    }

    @Override
    public byte getTypeId() {
        return TYPE_PRE_PREPARE;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public byte[] getBlockBytes() {
        return copy(blockBytes);
    }

    public void setBlockBytes(byte[] blockBytes) {
        this.blockBytes = copy(blockBytes);
    }

    public byte[] getProposerId() {
        return copy(proposerId);
    }

    public void setProposerId(byte[] proposerId) {
        this.proposerId = copy(proposerId);
    }

    public byte[] getProposerSignature() {
        return copy(proposerSignature);
    }

    public void setProposerSignature(byte[] proposerSignature) {
        this.proposerSignature = copy(proposerSignature);
    }
}
