package com.neuramesh.vm;

import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.processors.NodeRegisterProcessor;
import com.neuramesh.vm.processors.TaskSettleProcessor;
import com.neuramesh.vm.processors.TokenTransferProcessor;
import com.neuramesh.vm.processors.WeightUpdateProcessor;
import com.neuramesh.vm.state.AccountState;
import com.neuramesh.vm.state.GlobalState;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 交易状态机：按 {@link TxType} 路由到对应 {@link TransactionProcessor}。
 *
 * <p>{@link #apply(Transaction, GlobalState)} 流程：
 * <ol>
 *   <li>对 {@code state} 取快照；</li>
 *   <li>校验发起方 nonce（不匹配抛 {@code DUPLICATE_NONCE}）；</li>
 *   <li>调用处理器执行业务逻辑；</li>
 *   <li>成功则递增发起方 nonce，返回新的 state root；</li>
 *   <li>任意 {@link VMException} 则用快照回滚并抛出。</li>
 * </ol>
 *
 * <p>方法 {@code synchronized}：交易串行执行（BFT 共识保证顺序），从而并发提交也不会双花。
 */
public final class StateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(StateMachine.class);

    private final Map<TxType, TransactionProcessor> processors = new EnumMap<>(TxType.class);

    /**
     * 注册处理器（按其 {@link TransactionProcessor#getType()}）。
     *
     * @param processor 处理器
     */
    public void register(TransactionProcessor processor) {
        processors.put(processor.getType(), processor);
    }

    /**
     * 构建包含 4 种标准处理器的状态机。
     *
     * @param validators 验证者集（WEIGHT_UPDATE 见证验签所需）
     * @return 状态机
     */
    public static StateMachine standard(ValidatorSet validators) {
        StateMachine sm = new StateMachine();
        sm.register(new NodeRegisterProcessor());
        sm.register(new WeightUpdateProcessor(validators));
        sm.register(new TaskSettleProcessor());
        sm.register(new TokenTransferProcessor());
        return sm;
    }

    /**
     * 执行一笔交易并返回新的 state root（失败回滚并抛出）。
     *
     * @param tx    交易
     * @param state 全局状态（就地修改）
     * @return 执行后的 state root（32 字节）
     */
    public synchronized byte[] apply(Transaction tx, GlobalState state) {
        if (tx == null || state == null) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "apply 参数不可为 null");
        }
        TransactionProcessor processor = processors.get(tx.getType());
        if (processor == null) {
            throw new VMException(VMException.Kind.UNKNOWN_TX_TYPE, "无处理器: " + tx.getType());
        }
        GlobalState snapshot = state.snapshot();
        try {
            AccountState from = state.getOrCreateAccount(tx.getFrom());
            if (tx.getNonce() != from.getNonce()) {
                throw new VMException(VMException.Kind.DUPLICATE_NONCE,
                        "nonce 不匹配: 期望 " + from.getNonce() + "，实际 " + tx.getNonce());
            }
            processor.process(tx, state);
            from.incrementNonce();
            byte[] root = state.commit();
            LOG.info("交易执行成功 type={} txId={} root={}", tx.getType(),
                    CryptoUtils.toHex(tx.getTxId()).substring(0, 12),
                    CryptoUtils.toHex(root).substring(0, 12));
            return root;
        } catch (VMException e) {
            state.restoreFrom(snapshot);
            LOG.info("交易执行失败回滚 type={} 原因={}", tx.getType(), e.getKind());
            throw e;
        }
    }

    /**
     * 已注册处理器数量。
     *
     * @return 数量
     */
    public int processorCount() {
        return processors.size();
    }
}
