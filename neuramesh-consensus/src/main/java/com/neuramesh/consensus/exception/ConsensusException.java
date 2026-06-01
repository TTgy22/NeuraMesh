package com.neuramesh.consensus.exception;

import com.neuramesh.core.NeuraException;

/**
 * 共识层运行时异常。继承自 {@link NeuraException}，统一异常体系。
 */
public class ConsensusException extends NeuraException {

    private static final long serialVersionUID = 1L;

    public ConsensusException(String message) {
        super(message);
    }

    public ConsensusException(String message, Throwable cause) {
        super(message, cause);
    }
}
