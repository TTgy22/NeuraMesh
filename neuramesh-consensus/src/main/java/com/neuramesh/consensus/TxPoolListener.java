package com.neuramesh.consensus;

import com.neuramesh.core.Transaction;
import java.util.List;

/**
 * 交易池状态回调监听器。
 */
public interface TxPoolListener {

    /**
     * 交易成功加入池时回调。
     *
     * @param tx 加入的交易
     */
    void onTxAdded(Transaction tx);

    /**
     * 交易批量移出池（被打包消费）时回调。
     *
     * @param txs 被移出的交易列表
     */
    void onTxRemoved(List<Transaction> txs);
}
