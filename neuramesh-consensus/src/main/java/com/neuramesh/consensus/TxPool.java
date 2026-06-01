package com.neuramesh.consensus;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内存交易池。
 *
 * <p>核心结构：{@link LinkedBlockingQueue}（FIFO，容量上限 {@link #CAPACITY}）+ 去重集合
 * （按 txId 十六进制）。
 *
 * <ul>
 *   <li>{@link #addTransaction(Transaction)}：结构/签名校验 → 去重 → 入队（满时抛
 *       {@link TxPoolFullException}）。</li>
 *   <li>{@link #getTransactions(int)}：FIFO 批量取出（单次最多 {@link #MAX_BATCH}）。</li>
 *   <li>{@link #startAutoPacking}：以单线程 {@link ExecutorService} 周期性打包，避免并发打包导致双花。</li>
 * </ul>
 *
 * <p>线程安全：队列与去重集合均为并发结构；多线程可安全并发 {@code addTransaction}。
 *
 * <p>签名校验：默认仅做结构校验（txId/地址长度）。完整签名验签需公钥解析能力，随状态机在后续 Pause
 * 接入；调用方可通过 {@link #setValidator(TransactionValidator)} 注入自定义校验（例如带公钥的验签）。
 */
public final class TxPool {

    private static final Logger LOG = LoggerFactory.getLogger(TxPool.class);

    /** 交易池容量上限。 */
    public static final int CAPACITY = 10_000;

    /** 单次取出的最大交易数。 */
    public static final int MAX_BATCH = 100;

    /**
     * 交易校验策略。
     */
    @FunctionalInterface
    public interface TransactionValidator {
        /**
         * @param tx 待校验交易
         * @return 通过返回 true，否则该交易被丢弃
         */
        boolean validate(Transaction tx);
    }

    private final LinkedBlockingQueue<Transaction> queue = new LinkedBlockingQueue<>(CAPACITY);
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final List<TxPoolListener> listeners = new CopyOnWriteArrayList<>();

    private volatile TransactionValidator validator = TxPool::structurallyValid;
    private ExecutorService packerExecutor;
    private final AtomicBoolean packing = new AtomicBoolean(false);

    /**
     * 默认结构校验：txId 为 32 字节、from/to 为合法地址长度。
     *
     * @param tx 交易
     * @return 是否结构合法
     */
    public static boolean structurallyValid(Transaction tx) {
        if (tx == null) {
            return false;
        }
        return tx.getTxId().length == 32
                && tx.getFrom().length == CryptoUtils.ADDRESS_LENGTH
                && tx.getTo().length == CryptoUtils.ADDRESS_LENGTH;
    }

    /**
     * 设置交易校验策略。
     *
     * @param validator 校验器（不可为 null）
     */
    public void setValidator(TransactionValidator validator) {
        if (validator == null) {
            throw new NullPointerException("validator");
        }
        this.validator = validator;
    }

    /**
     * 注册监听器。
     *
     * @param listener 监听器
     */
    public void addListener(TxPoolListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 加入一笔交易。
     *
     * @param tx 交易
     * @return 成功入池返回 true；校验失败或重复返回 false
     * @throws TxPoolFullException 池满
     */
    public boolean addTransaction(Transaction tx) {
        if (tx == null) {
            throw new TxPoolFullException("交易不可为 null");
        }
        if (!validator.validate(tx)) {
            LOG.debug("交易校验未通过，丢弃");
            return false;
        }
        String id = CryptoUtils.toHex(tx.getTxId());
        if (!seen.add(id)) {
            LOG.debug("交易 {} 重复，丢弃", id);
            return false;
        }
        if (!queue.offer(tx)) {
            seen.remove(id);
            throw new TxPoolFullException("交易池已满（容量 " + CAPACITY + "）");
        }
        for (TxPoolListener l : listeners) {
            try {
                l.onTxAdded(tx);
            } catch (Exception e) {
                LOG.warn("onTxAdded 监听器异常: {}", e.getMessage());
            }
        }
        return true;
    }

    /**
     * FIFO 批量取出交易（最多 {@code min(maxCount, MAX_BATCH)} 笔）。
     *
     * @param maxCount 期望取出数量
     * @return 取出的交易列表（可能为空，永不为 null）
     */
    public List<Transaction> getTransactions(int maxCount) {
        int limit = Math.min(Math.max(0, maxCount), MAX_BATCH);
        List<Transaction> batch = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Transaction tx = queue.poll();
            if (tx == null) {
                break;
            }
            batch.add(tx);
        }
        if (!batch.isEmpty()) {
            for (TxPoolListener l : listeners) {
                try {
                    l.onTxRemoved(batch);
                } catch (Exception e) {
                    LOG.warn("onTxRemoved 监听器异常: {}", e.getMessage());
                }
            }
        }
        return batch;
    }

    /**
     * 当前待处理交易数。
     *
     * @return 数量
     */
    public int size() {
        return queue.size();
    }

    /**
     * 是否包含某 txId。
     *
     * @param txId 交易 ID
     * @return 是否已见过
     */
    public boolean contains(byte[] txId) {
        return seen.contains(CryptoUtils.toHex(txId));
    }

    /**
     * 启动单线程周期打包。每隔 {@code periodMillis} 取出一批交易交给 {@code packer}。
     *
     * @param packer       打包消费者
     * @param periodMillis 周期（毫秒）
     */
    public void startAutoPacking(java.util.function.Consumer<List<Transaction>> packer, long periodMillis) {
        if (packer == null) {
            throw new NullPointerException("packer");
        }
        if (!packing.compareAndSet(false, true)) {
            return;
        }
        this.packerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "neura-txpool-packer");
            t.setDaemon(true);
            return t;
        });
        ((java.util.concurrent.ScheduledExecutorService) packerExecutor).scheduleAtFixedRate(() -> {
            List<Transaction> batch = getTransactions(MAX_BATCH);
            if (!batch.isEmpty()) {
                packer.accept(batch);
            }
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        LOG.info("TxPool 自动打包启动，周期 {}ms", periodMillis);
    }

    /**
     * 停止自动打包。
     */
    public void stopAutoPacking() {
        if (packing.compareAndSet(true, false) && packerExecutor != null) {
            packerExecutor.shutdownNow();
        }
    }
}
