package com.neuramesh.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.storage.ColumnFamilies;
import com.neuramesh.storage.RocksDBStore;
import com.neuramesh.storage.StorageException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocksDBStoreTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("基础读写：put/get/delete 在 4 个分区下均工作")
    void basic_put_get_delete_per_partition(@TempDir Path dir) throws Exception {
        try (RocksDBStore store = new RocksDBStore(dir)) {
            for (ColumnFamilies cf : ColumnFamilies.values()) {
                store.put(cf, bytes("k1"), bytes("v-" + cf));
                assertThat(store.get(cf, bytes("k1")))
                        .as("分区 %s 应能读出写入值", cf)
                        .isEqualTo(bytes("v-" + cf));
            }
            store.delete(ColumnFamilies.BLOCKS, bytes("k1"));
            assertThat(store.get(ColumnFamilies.BLOCKS, bytes("k1"))).isNull();
            assertThat(store.get(ColumnFamilies.STATE, bytes("k1"))).isEqualTo(bytes("v-STATE"));
        }
    }

    @Test
    @DisplayName("分区隔离：相同业务键在不同分区互不干扰")
    void partitions_are_isolated(@TempDir Path dir) throws Exception {
        try (RocksDBStore store = new RocksDBStore(dir)) {
            store.put(ColumnFamilies.BLOCKS, bytes("same-key"), bytes("blocks-value"));
            store.put(ColumnFamilies.STATE, bytes("same-key"), bytes("state-value"));
            assertThat(store.get(ColumnFamilies.BLOCKS, bytes("same-key"))).isEqualTo(bytes("blocks-value"));
            assertThat(store.get(ColumnFamilies.STATE, bytes("same-key"))).isEqualTo(bytes("state-value"));
        }
    }

    @Test
    @DisplayName("字符串分区名 API：与枚举 API 等价")
    void string_partition_api_equivalent(@TempDir Path dir) throws Exception {
        try (RocksDBStore store = new RocksDBStore(dir)) {
            store.put("BLOCKS", bytes("k"), bytes("v"));
            assertThat(store.get("BLOCKS", bytes("k"))).isEqualTo(bytes("v"));
            store.delete("BLOCKS", bytes("k"));
            assertThat(store.get("BLOCKS", bytes("k"))).isNull();
            assertThatThrownBy(() -> store.put("UNKNOWN", bytes("k"), bytes("v")))
                    .isInstanceOf(StorageException.class);
        }
    }

    @Test
    @DisplayName("持久化：重启后数据仍可读出")
    void data_persists_across_reopen(@TempDir Path dir) throws Exception {
        try (RocksDBStore store = new RocksDBStore(dir)) {
            store.put(ColumnFamilies.META, bytes("genesis"), bytes("hash-0"));
        }
        try (RocksDBStore reopened = new RocksDBStore(dir)) {
            assertThat(reopened.get(ColumnFamilies.META, bytes("genesis"))).isEqualTo(bytes("hash-0"));
        }
    }

    @Test
    @DisplayName("并发读写：10 线程 × 1000 次写入读取保持一致")
    void concurrent_read_write(@TempDir Path dir) throws Exception {
        final int threadCount = 10;
        final int iterations = 1000;
        try (RocksDBStore store = new RocksDBStore(dir)) {
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger failures = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            byte[] key = bytes("t" + threadId + "-k" + i);
                            byte[] value = bytes("v" + threadId + "-" + i);
                            store.put(ColumnFamilies.STATE, key, value);
                            byte[] readBack = store.get(ColumnFamilies.STATE, key);
                            if (readBack == null
                                    || !java.util.Arrays.equals(readBack, value)) {
                                failures.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(failures.get()).isZero();
        }
    }

    @Test
    @DisplayName("close 之后操作抛出 StorageException；幂等关闭")
    void operations_after_close_throw(@TempDir Path dir) throws Exception {
        RocksDBStore store = new RocksDBStore(dir);
        store.put(ColumnFamilies.META, bytes("k"), bytes("v"));
        store.close();
        assertThat(store.isClosed()).isTrue();
        assertThatThrownBy(() -> store.get(ColumnFamilies.META, bytes("k")))
                .isInstanceOf(StorageException.class);
        store.close();
        assertThat(store.isClosed()).isTrue();
    }

    @Test
    @DisplayName("getDefault: Holder 单例模式返回同一实例；closeDefault 清空")
    void default_holder_singleton(@TempDir Path dir) {
        try {
            RocksDBStore a = RocksDBStore.getDefault(dir);
            RocksDBStore b = RocksDBStore.getDefault(dir);
            assertThat(a).isSameAs(b);
            a.put(ColumnFamilies.META, bytes("k"), bytes("v"));
            assertThat(b.get(ColumnFamilies.META, bytes("k"))).isEqualTo(bytes("v"));
        } finally {
            RocksDBStore.closeDefault();
        }
    }
}
