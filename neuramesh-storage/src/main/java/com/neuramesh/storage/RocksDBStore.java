package com.neuramesh.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocksDB 存储封装。
 *
 * <p>实现策略：
 * <ul>
 *   <li>单 ColumnFamily（默认）+ 键前缀分区，分区由 {@link ColumnFamilies} 管理。</li>
 *   <li>写入开启同步 WAL（{@link WriteOptions#setSync(boolean)} = true），保证进程重启后数据持久化。</li>
 *   <li>对外 API：{@link #put}、{@link #get}、{@link #delete}，键值均为 {@code byte[]}。</li>
 *   <li>线程安全：RocksDB JNI 句柄本身线程安全；本类使用 {@link AtomicBoolean} 防止重复关闭。</li>
 * </ul>
 *
 * <p>本类实现 {@link AutoCloseable}，建议使用 try-with-resources 管理；同时本项目以 Holder 单例模式
 * 暴露默认实例（{@link #getDefault(Path)}），便于全局共享一份 RocksDB 句柄。
 */
public final class RocksDBStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RocksDBStore.class);

    static {
        RocksDB.loadLibrary();
    }

    private final Path dataDir;
    private final RocksDB db;
    private final Options options;
    private final WriteOptions syncWriteOptions;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 打开（或创建）位于 {@code dataDir} 的 RocksDB 实例。
     *
     * @param dataDir 数据目录（不存在时自动创建）
     */
    public RocksDBStore(Path dataDir) {
        if (dataDir == null) {
            throw new StorageException("dataDir 不可为 null");
        }
        this.dataDir = dataDir;
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new StorageException("创建数据目录失败: " + dataDir, e);
        }

        this.options = new Options().setCreateIfMissing(true);
        this.syncWriteOptions = new WriteOptions().setSync(true);
        try {
            this.db = RocksDB.open(options, dataDir.toAbsolutePath().toString());
            LOG.info("RocksDB opened at {}", dataDir.toAbsolutePath());
        } catch (RocksDBException e) {
            options.close();
            syncWriteOptions.close();
            throw new StorageException("RocksDB 打开失败: " + dataDir, e);
        }
    }

    /**
     * 写入键值（同步 WAL）。
     *
     * @param cf    分区
     * @param key   业务键（拼接前缀后实际写入）
     * @param value 值
     */
    public void put(ColumnFamilies cf, byte[] key, byte[] value) {
        ensureOpen();
        if (cf == null || key == null || value == null) {
            throw new StorageException("put 参数不可为 null");
        }
        try {
            db.put(syncWriteOptions, cf.composeKey(key), value);
        } catch (RocksDBException e) {
            throw new StorageException("RocksDB put 失败: " + cf + "/" + key.length + " bytes", e);
        }
    }

    /**
     * 字符串分区名版本的 put（便捷方法，匹配提示词签名）。
     *
     * @param columnFamily 分区名（大小写敏感，参见 {@link ColumnFamilies#fromName(String)}）
     * @param key          业务键
     * @param value        值
     */
    public void put(String columnFamily, byte[] key, byte[] value) {
        put(ColumnFamilies.fromName(columnFamily), key, value);
    }

    /**
     * 读取键对应的值。
     *
     * @param cf  分区
     * @param key 业务键
     * @return 值（不存在返回 {@code null}）
     */
    public byte[] get(ColumnFamilies cf, byte[] key) {
        ensureOpen();
        if (cf == null || key == null) {
            throw new StorageException("get 参数不可为 null");
        }
        try {
            return db.get(cf.composeKey(key));
        } catch (RocksDBException e) {
            throw new StorageException("RocksDB get 失败: " + cf, e);
        }
    }

    /**
     * 字符串分区名版本的 get（便捷方法）。
     *
     * @param columnFamily 分区名
     * @param key          业务键
     * @return 值（不存在返回 {@code null}）
     */
    public byte[] get(String columnFamily, byte[] key) {
        return get(ColumnFamilies.fromName(columnFamily), key);
    }

    /**
     * 删除键。
     *
     * @param cf  分区
     * @param key 业务键
     */
    public void delete(ColumnFamilies cf, byte[] key) {
        ensureOpen();
        if (cf == null || key == null) {
            throw new StorageException("delete 参数不可为 null");
        }
        try {
            db.delete(syncWriteOptions, cf.composeKey(key));
        } catch (RocksDBException e) {
            throw new StorageException("RocksDB delete 失败: " + cf, e);
        }
    }

    /**
     * 字符串分区名版本的 delete（便捷方法）。
     *
     * @param columnFamily 分区名
     * @param key          业务键
     */
    public void delete(String columnFamily, byte[] key) {
        delete(ColumnFamilies.fromName(columnFamily), key);
    }

    /**
     * 数据目录路径。
     *
     * @return 数据目录
     */
    public Path getDataDir() {
        return dataDir;
    }

    /**
     * 是否已关闭。
     *
     * @return 已调用 {@link #close()} 时返回 true
     */
    public boolean isClosed() {
        return closed.get();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new StorageException("RocksDBStore 已关闭");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                db.close();
            } finally {
                syncWriteOptions.close();
                options.close();
            }
            LOG.info("RocksDB closed at {}", dataDir.toAbsolutePath());
        }
    }

    // ------------------------------------------------------------------
    // Holder 单例模式（默认实例）
    // ------------------------------------------------------------------

    private static volatile RocksDBStore defaultInstance;

    /**
     * 获取或初始化全局默认实例。首次调用时 {@code dataDir} 必填；后续调用忽略 {@code dataDir}，
     * 直接返回首次创建的实例。如需更换路径，需先调用 {@link #closeDefault()}。
     *
     * @param dataDir 默认实例的数据目录
     * @return 默认 {@link RocksDBStore} 实例
     */
    public static RocksDBStore getDefault(Path dataDir) {
        RocksDBStore local = defaultInstance;
        if (local == null) {
            synchronized (RocksDBStore.class) {
                local = defaultInstance;
                if (local == null) {
                    local = new RocksDBStore(dataDir);
                    defaultInstance = local;
                }
            }
        }
        return local;
    }

    /**
     * 关闭并清空默认实例（多用于测试或停机收尾）。
     */
    public static void closeDefault() {
        synchronized (RocksDBStore.class) {
            if (defaultInstance != null) {
                defaultInstance.close();
                defaultInstance = null;
            }
        }
    }
}
