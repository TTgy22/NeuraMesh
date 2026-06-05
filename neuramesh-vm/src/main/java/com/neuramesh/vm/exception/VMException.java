package com.neuramesh.vm.exception;

import com.neuramesh.core.NeuraException;

/**
 * 状态机执行异常。继承自 {@link NeuraException}，并携带细分原因 {@link Kind}。
 */
public class VMException extends NeuraException {

    private static final long serialVersionUID = 1L;

    /**
     * 失败原因细分。
     */
    public enum Kind {
        INSUFFICIENT_BALANCE,
        INVALID_SIGNATURE,
        DUPLICATE_NONCE,
        INVALID_WEIGHT_ATTESTATION,
        DUPLICATE_REGISTRATION,
        INVALID_PAYLOAD,
        UNKNOWN_TX_TYPE
    }

    private final Kind kind;

    public VMException(Kind kind, String message) {
        super("[" + kind + "] " + message);
        this.kind = kind;
    }

    public VMException(Kind kind, String message, Throwable cause) {
        super("[" + kind + "] " + message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
