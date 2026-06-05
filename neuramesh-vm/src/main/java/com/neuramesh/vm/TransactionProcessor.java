package com.neuramesh.vm;

import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.state.GlobalState;

/**
 * 交易处理器接口。
 *
 * <p>纯逻辑：仅基于入参 {@link Transaction} 读写 {@link GlobalState} 内存对象，
 * 严禁直接访问 RocksDB 或产生其他副作用。失败时抛出
 * {@link com.neuramesh.vm.exception.VMException}，由 {@code StateMachine} 统一回滚。
 */
public interface TransactionProcessor {

    /**
     * 处理一笔交易（在状态机已做 nonce 校验之后调用）。
     *
     * @param tx    交易
     * @param state 全局状态（就地修改）
     */
    void process(Transaction tx, GlobalState state);

    /**
     * 处理的交易类型。
     *
     * @return 类型
     */
    TxType getType();
}
