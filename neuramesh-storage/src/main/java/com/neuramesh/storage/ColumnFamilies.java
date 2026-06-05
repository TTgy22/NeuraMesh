package com.neuramesh.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 存储分区枚举。
 *
 * <p>RocksDB 默认 ColumnFamily 名为 "default"。本项目统一使用单 ColumnFamily + 键前缀的简化方案：
 * 每个分区对应一个 ASCII 前缀，写入时自动拼接为 {@code prefix + ":" + key}，避免跨区冲突。
 */
public enum ColumnFamilies {

    BLOCKS("blocks"),
    TRANSACTIONS("transactions"),
    STATE("state"),
    NODES("nodes"),
    META("meta");

    private final String prefix;
    private final byte[] prefixBytes;

    ColumnFamilies(String prefix) {
        this.prefix = prefix;
        this.prefixBytes = (prefix + ":").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 分区前缀（不含分隔符）。
     *
     * @return 分区前缀字符串
     */
    public String prefix() {
        return prefix;
    }

    /**
     * 将业务键拼接为带分区前缀的实际存储键：{@code prefix:key}。
     *
     * @param key 业务键（不可为 null）
     * @return 带前缀的字节键
     */
    public byte[] composeKey(byte[] key) {
        if (key == null) {
            throw new StorageException("key 不可为 null");
        }
        byte[] out = new byte[prefixBytes.length + key.length];
        System.arraycopy(prefixBytes, 0, out, 0, prefixBytes.length);
        System.arraycopy(key, 0, out, prefixBytes.length, key.length);
        return out;
    }

    /**
     * 由分区名（区分大小写：BLOCKS/TRANSACTIONS/STATE/META）解析枚举。
     *
     * @param name 分区名
     * @return 对应枚举值
     */
    public static ColumnFamilies fromName(String name) {
        if (name == null) {
            throw new StorageException("分区名不可为 null");
        }
        return Arrays.stream(values())
                .filter(cf -> cf.name().equals(name) || cf.prefix.equals(name))
                .findFirst()
                .orElseThrow(() -> new StorageException("未知分区: " + name));
    }
}
