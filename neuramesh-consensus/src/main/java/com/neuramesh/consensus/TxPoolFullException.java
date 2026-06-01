package com.neuramesh.consensus;

import com.neuramesh.core.NeuraException;

/**
 * 交易池已满异常。当待处理交易数达到容量上限时，新交易入池将抛出此异常。
 */
public class TxPoolFullException extends NeuraException {

    private static final long serialVersionUID = 1L;

    public TxPoolFullException(String message) {
        super(message);
    }
}
