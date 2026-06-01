package com.neuramesh.network;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.NeuraException;
import java.util.Arrays;

/**
 * 节点身份标识。
 *
 * <p>由 20 字节组成（与账户地址同长度），通常等同于节点签名公钥派生的地址。
 * 不可变值对象；equals 与 hashCode 基于字节内容。
 */
public final class NodeId {

    /** NodeId 字节长度，与账户地址保持一致。 */
    public static final int LENGTH = CryptoUtils.ADDRESS_LENGTH;

    private final byte[] bytes;

    private NodeId(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    /**
     * 由原始字节构造 NodeId（防御性拷贝）。
     *
     * @param bytes LENGTH 字节
     * @return NodeId 实例
     */
    public static NodeId of(byte[] bytes) {
        if (bytes == null || bytes.length != LENGTH) {
            throw new NeuraException("NodeId 长度需为 " + LENGTH);
        }
        return new NodeId(bytes);
    }

    /**
     * 由十六进制字符串构造 NodeId。
     *
     * @param hex 长度为 LENGTH * 2 的十六进制字符串
     * @return NodeId 实例
     */
    public static NodeId fromHex(String hex) {
        if (hex == null || hex.length() != LENGTH * 2) {
            throw new NeuraException("NodeId hex 长度需为 " + LENGTH * 2);
        }
        byte[] data = new byte[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new NeuraException("NodeId hex 非法字符");
            }
            data[i] = (byte) ((hi << 4) | lo);
        }
        return new NodeId(data);
    }

    /**
     * 返回字节数组（防御性拷贝）。
     *
     * @return 长度为 LENGTH 的字节数组
     */
    public byte[] toBytes() {
        return bytes.clone();
    }

    /**
     * 返回十六进制字符串。
     *
     * @return 小写 hex 字符串
     */
    public String toHex() {
        return CryptoUtils.toHex(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeId other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "NodeId(" + toHex() + ")";
    }
}