package com.neuramesh.network.messages;

import com.neuramesh.network.NeuraMessage;

/**
 * COMMIT 投票线路消息（PBFT 第三阶段，typeId=0x09）。
 */
public class CommitMessage extends NeuraMessage {

    private byte[] blockHash;
    private byte[] validatorId;
    private byte[] signature;

    public CommitMessage() {
        super();
    }

    public CommitMessage(byte[] blockHash, byte[] validatorId, byte[] signature) {
        super();
        this.blockHash = copy(blockHash);
        this.validatorId = copy(validatorId);
        this.signature = copy(signature);
    }

    private static byte[] copy(byte[] b) {
        return (b == null) ? new byte[0] : b.clone();
    }

    @Override
    public byte getTypeId() {
        return TYPE_COMMIT;
    }

    public byte[] getBlockHash() {
        return copy(blockHash);
    }

    public void setBlockHash(byte[] blockHash) {
        this.blockHash = copy(blockHash);
    }

    public byte[] getValidatorId() {
        return copy(validatorId);
    }

    public void setValidatorId(byte[] validatorId) {
        this.validatorId = copy(validatorId);
    }

    public byte[] getSignature() {
        return copy(signature);
    }

    public void setSignature(byte[] signature) {
        this.signature = copy(signature);
    }
}
