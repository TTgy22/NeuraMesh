package com.neuramesh.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.storage.ColumnFamilies;
import com.neuramesh.storage.RocksDBStore;
import com.neuramesh.storage.StorageException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 存储层参数校验与边缘分支测试，主要用于覆盖 null 参数 / 未知分区 / 关闭后访问等防御性分支。
 */
class StorageValidationTest {

    @Test
    @DisplayName("ColumnFamilies: composeKey/fromName 对 null 与未知名称抛出异常")
    void column_families_validation() {
        assertThatThrownBy(() -> ColumnFamilies.BLOCKS.composeKey(null))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> ColumnFamilies.fromName(null))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> ColumnFamilies.fromName("UNKNOWN_CF"))
                .isInstanceOf(StorageException.class);
        // 通过 prefix 反查也应成功
        assertThat(ColumnFamilies.fromName("blocks")).isEqualTo(ColumnFamilies.BLOCKS);
    }

    @Test
    @DisplayName("RocksDBStore 构造: dataDir = null 抛出异常")
    void store_constructor_null_dir() {
        assertThatThrownBy(() -> new RocksDBStore(null))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("RocksDBStore put/get/delete 各路径对 null 抛出异常")
    void store_null_param_validation(@TempDir Path dir) throws Exception {
        try (RocksDBStore store = new RocksDBStore(dir)) {
            byte[] k = new byte[] {1};
            byte[] v = new byte[] {2};

            assertThatThrownBy(() -> store.put((ColumnFamilies) null, k, v))
                    .isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> store.put(ColumnFamilies.STATE, null, v))
                    .isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> store.put(ColumnFamilies.STATE, k, null))
                    .isInstanceOf(StorageException.class);

            assertThatThrownBy(() -> store.get((ColumnFamilies) null, k))
                    .isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> store.get(ColumnFamilies.STATE, null))
                    .isInstanceOf(StorageException.class);

            assertThatThrownBy(() -> store.delete((ColumnFamilies) null, k))
                    .isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> store.delete(ColumnFamilies.STATE, null))
                    .isInstanceOf(StorageException.class);
        }
    }
}
