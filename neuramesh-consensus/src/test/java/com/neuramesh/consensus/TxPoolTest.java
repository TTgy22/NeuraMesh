package com.neuramesh.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TxPoolTest {

    private static byte[] addr(int seed) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (seed + i);
        }
        return a;
    }

    private static Transaction tx(long nonce) {
        return Transaction.create(TxType.TOKEN_TRANSFER, addr(1), addr(2), nonce,
                new byte[] {1}, 1_700_000_000_000L);
    }

    @Test
    @DisplayName("基础：加入、去重、size、FIFO 取出")
    void basic_add_dedup_fifo() {
        TxPool pool = new TxPool();
        Transaction t1 = tx(1);
        Transaction t2 = tx(2);

        assertThat(pool.addTransaction(t1)).isTrue();
        assertThat(pool.addTransaction(t2)).isTrue();
        assertThat(pool.addTransaction(t1)).as("重复交易应被丢弃").isFalse();
        assertThat(pool.size()).isEqualTo(2);

        List<Transaction> batch = pool.getTransactions(10);
        assertThat(batch).hasSize(2);
        // FIFO：t1 在 t2 之前
        assertThat(batch.get(0).getTxId()).containsExactly(t1.getTxId());
        assertThat(batch.get(1).getTxId()).containsExactly(t2.getTxId());
        assertThat(pool.size()).isZero();
    }

    @Test
    @DisplayName("getTransactions 单次上限为 MAX_BATCH")
    void get_transactions_capped_at_max_batch() {
        TxPool pool = new TxPool();
        for (int i = 0; i < TxPool.MAX_BATCH + 50; i++) {
            pool.addTransaction(tx(i));
        }
        List<Transaction> batch = pool.getTransactions(1000);
        assertThat(batch).hasSize(TxPool.MAX_BATCH);
        assertThat(pool.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("结构非法交易被拒绝")
    void invalid_transaction_rejected() {
        TxPool pool = new TxPool();
        pool.setValidator(t -> false);
        assertThat(pool.addTransaction(tx(1))).isFalse();
        assertThat(pool.size()).isZero();
        assertThatThrownBy(() -> pool.addTransaction(null)).isInstanceOf(TxPoolFullException.class);
    }

    @Test
    @DisplayName("监听器回调 onTxAdded / onTxRemoved")
    void listener_callbacks() {
        TxPool pool = new TxPool();
        AtomicInteger added = new AtomicInteger();
        AtomicInteger removed = new AtomicInteger();
        pool.addListener(new TxPoolListener() {
            @Override
            public void onTxAdded(Transaction tx) {
                added.incrementAndGet();
            }

            @Override
            public void onTxRemoved(List<Transaction> txs) {
                removed.addAndGet(txs.size());
            }
        });
        pool.addTransaction(tx(1));
        pool.addTransaction(tx(2));
        pool.getTransactions(10);
        assertThat(added.get()).isEqualTo(2);
        assertThat(removed.get()).isEqualTo(2);
    }

    @Test
    @Timeout(30)
    @DisplayName("并发：10 线程 × 100 笔写入，无丢失无重复")
    void concurrent_writes_no_loss_no_dup() throws InterruptedException {
        final TxPool pool = new TxPool();
        final int threads = 10;
        final int perThread = 100;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            exec.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        if (pool.addTransaction(tx(base + i))) {
                            accepted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        exec.shutdownNow();

        assertThat(accepted.get()).isEqualTo(threads * perThread);
        assertThat(pool.size()).isEqualTo(threads * perThread);

        // 全部取出，验证 txId 无重复
        Set<String> ids = new HashSet<>();
        List<Transaction> all = new ArrayList<>();
        List<Transaction> batch;
        while (!(batch = pool.getTransactions(TxPool.MAX_BATCH)).isEmpty()) {
            all.addAll(batch);
        }
        assertThat(all).hasSize(threads * perThread);
        for (Transaction t : all) {
            ids.add(CryptoUtils.toHex(t.getTxId()));
        }
        assertThat(ids).hasSize(threads * perThread);
    }

    @Test
    @Timeout(30)
    @DisplayName("自动打包：单线程消费消费交易")
    void auto_packing_consumes() throws InterruptedException {
        TxPool pool = new TxPool();
        for (int i = 0; i < 50; i++) {
            pool.addTransaction(tx(i));
        }
        List<Transaction> packed = java.util.Collections.synchronizedList(new ArrayList<>());
        pool.startAutoPacking(packed::addAll, 50);
        try {
            assertThat(TestWait.until(() -> packed.size() == 50, 5_000)).isTrue();
            assertThat(pool.size()).isZero();
        } finally {
            pool.stopAutoPacking();
        }
    }

    /** 简易轮询等待工具。 */
    static final class TestWait {
        static boolean until(java.util.function.BooleanSupplier cond, long timeoutMillis)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                if (cond.getAsBoolean()) {
                    return true;
                }
                Thread.sleep(20);
            }
            return cond.getAsBoolean();
        }
    }
}
