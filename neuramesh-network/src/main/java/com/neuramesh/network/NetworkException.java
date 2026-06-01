package com.neuramesh.network;

import com.neuramesh.core.NeuraException;

/**
 * 网络层运行时异常。继承自 {@link NeuraException}，用于统一异常体系。
 */
public class NetworkException extends NeuraException {

    private static final long serialVersionUID = 1L;

    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}