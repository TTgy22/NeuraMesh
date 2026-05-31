package com.neuramesh.core;

/**
 * 交易类型枚举（锁定 4 种，不可扩展）。
 */
public enum TxType {

    /** 节点注册。 */
    NODE_REGISTER,

    /** 权重更新（需 3 见证签名）。 */
    WEIGHT_UPDATE,

    /** 任务结算（按权重分配）。 */
    TASK_SETTLE,

    /** NMT 转账。 */
    TOKEN_TRANSFER
}
