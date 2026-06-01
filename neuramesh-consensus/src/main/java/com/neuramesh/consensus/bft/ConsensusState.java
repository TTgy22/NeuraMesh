package com.neuramesh.consensus.bft;

/**
 * 共识状态机的状态枚举。
 *
 * <p>正常路径：IDLE → PROPOSING → PREPARING → COMMITTING → FINALIZED。
 */
public enum ConsensusState {

    /** 空闲，未开始某高度的共识。 */
    IDLE,

    /** 提案阶段：等待/产生 PrePrepare。 */
    PROPOSING,

    /** 准备阶段：已接受提案，收集 PREPARE 投票。 */
    PREPARING,

    /** 提交阶段：PREPARE 达到法定人数，收集 COMMIT 投票。 */
    COMMITTING,

    /** 最终化：COMMIT 达到法定人数，区块不可逆。 */
    FINALIZED
}
