package com.neuramesh.consensus.bft;

/**
 * 共识消息广播抽象（传输无关）。
 *
 * <p>{@link BFTConsensus} 通过本接口向其他验证者广播三阶段消息，从而与具体传输（真实 Netty P2P
 * 或测试用内存总线）解耦。生产环境由 network 层提供基于 {@code P2PNetwork} 的实现；
 * 测试用内存实现可确定性地驱动多节点。
 */
public interface ConsensusBroadcaster {

    /**
     * 广播 PrePrepare 提案给其他验证者。
     *
     * @param prePrepare 提案
     */
    void broadcastPrePrepare(PrePrepare prePrepare);

    /**
     * 广播 PREPARE 投票给其他验证者。
     *
     * @param vote 投票
     */
    void broadcastPrepare(Vote vote);

    /**
     * 广播 COMMIT 投票给其他验证者。
     *
     * @param vote 投票
     */
    void broadcastCommit(Vote vote);
}
