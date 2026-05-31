package com.neuramesh.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 字节工具类：长整型 / 整型与字节数组的小端互转，用于哈希前置编码。
 *
 * <p>使用小端序的考量：本项目内部哈希自洽即可；为与 RocksDB 比较语义解耦，键编码不依赖此处。
 */
public final class ByteUtils {

    private ByteUtils() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 长整型转 8 字节小端字节数组。
     *
     * @param value 长整型值
     * @return 8 字节数组
     */
    public static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    /**
     * 整型转 4 字节小端字节数组。
     *
     * @param value 整型值
     * @return 4 字节数组
     */
    public static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    /**
     * 拼接多段字节数组。
     *
     * @param parts 字节片段
     * @return 拼接后的字节数组
     */
    public static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            if (part == null) {
                throw new NeuraException("concat 片段不可为 null");
            }
            total += part.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
