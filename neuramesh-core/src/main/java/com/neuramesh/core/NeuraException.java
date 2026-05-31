package com.neuramesh.core;

/**
 * NeuraMesh 统一运行时异常基类。
 *
 * <p>项目内所有自定义异常均应继承此类，禁止吞异常（catch 后必须包装抛出或记录日志）。
 */
public class NeuraException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NeuraException(String message) {
        super(message);
    }

    public NeuraException(String message, Throwable cause) {
        super(message, cause);
    }
}
