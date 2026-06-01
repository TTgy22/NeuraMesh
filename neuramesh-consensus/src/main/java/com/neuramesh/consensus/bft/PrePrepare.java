package com.neuramesh.consensus.bft;

import com.neuramesh.consensus.exception.ConsensusException;
import com.neuramesh.core.Block;
import com.neuramesh.core.CryptoUtils;

/**
 * PrePrepare 提案（PBFT 第一阶段）。
 *
 * <p>携带提案区块、提案人节点地址，以及提案人对 {@code block.getHash()} 的签名。
 * 区块的 {@code validatorSig} 字段保持为空，使区块哈希独立于签名（避免循环依赖），
 * 提案人签名单独随本消息传播。
 */
public final class PrePrepare {

    private final long height;
    private final Block block;
    private final byte[] proposerId;
    private final byte[] proposerSignature;

    public PrePrepare(long height, Block block, byte[] proposerId, byte[] proposerSignature) {
        if (block == null) {
            throw new ConsensusException("提案区块不可为 null");
        }
        if (proposerId == null || proposerId.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new ConsensusException("proposerId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        if (proposerSignature == null || proposerSignature.length == 0) {
            throw new ConsensusException("提案签名不可为空");
        }
        this.height = height;
        this.block = block;
        this.proposerId = proposerId.clone();
        this.proposerSignature = proposerSignature.clone();
    }

    public long getHeight() {
        return height;
    }

    public Block getBlock() {
        return block;
    }

    public byte[] getProposerId() {
        return proposerId.clone();
    }

    public byte[] getProposerSignature() {
        return proposerSignature.clone();
    }

    /**
     * 验证提案人签名（对 block.getHash()）。
     *
     * @param validators 验证者集
     * @return 提案人确为验证者且签名有效
     */
    public boolean verify(ValidatorSet validators) {
        Validator proposer = validators.getByNodeId(proposerId);
        if (proposer == null) {
            return false;
        }
        return CryptoUtils.verify(block.getHash(), proposerSignature, proposer.getPublicKey());
    }
}
