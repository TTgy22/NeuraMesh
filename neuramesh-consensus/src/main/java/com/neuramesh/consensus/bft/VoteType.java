package com.neuramesh.consensus.bft;

/**
 * 投票阶段类型（PBFT 三阶段中的后两个投票阶段）。
 */
public enum VoteType {

    /** 准备投票。 */
    PREPARE,

    /** 提交投票。 */
    COMMIT
}
