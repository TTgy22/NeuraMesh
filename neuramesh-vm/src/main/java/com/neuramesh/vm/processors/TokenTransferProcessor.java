package com.neuramesh.vm.processors;

import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.TransactionProcessor;
import com.neuramesh.vm.payload.TokenTransferPayload;
import com.neuramesh.vm.state.AccountState;
import com.neuramesh.vm.state.GlobalState;

/**
 * TOKEN_TRANSFER 处理器：from → to 转账 amount（余额校验，防双花由状态机 nonce + 串行执行保证）。
 */
public final class TokenTransferProcessor implements TransactionProcessor {

    @Override
    public void process(Transaction tx, GlobalState state) {
        TokenTransferPayload p = TokenTransferPayload.decode(tx.getPayload());
        AccountState from = state.getOrCreateAccount(tx.getFrom());
        AccountState to = state.getOrCreateAccount(tx.getTo());
        from.debit(p.amount());
        to.credit(p.amount());
    }

    @Override
    public TxType getType() {
        return TxType.TOKEN_TRANSFER;
    }
}
