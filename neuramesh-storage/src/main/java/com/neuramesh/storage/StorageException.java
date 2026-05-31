package com.neuramesh.storage;

import com.neuramesh.core.NeuraException;

/**
 * 存储层运行时异常。继承自 {@link NeuraException}，统一异常体系。
 */
public class StorageException extends NeuraException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
