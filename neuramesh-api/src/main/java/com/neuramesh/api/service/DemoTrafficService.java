package com.neuramesh.api.service;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.payload.TokenTransferPayload;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 演示流量发生器（可开关，默认关闭）：开启后定时提交小额真实 TOKEN_TRANSFER（两个演示账户互转），
 * 驱动真实共识管线持续出块，使网络吞吐曲线产生可见波动。
 *
 * <p>每笔均为真实交易（入池 → BFT 三阶段 → 出块 → 状态机执行），非前端假数据；
 * 两账户互转保证余额长期守恒，不影响节点 / 用户 / 资源组任何状态。
 * 经 {@code GET/POST /chain/demo-traffic} 查询与开关。
 */
@Service
public class DemoTrafficService {

    private static final Logger LOG = LoggerFactory.getLogger(DemoTrafficService.class);
    /** 定时间隔（毫秒）：略小于前端 5s 采样窗口，曲线起伏自然。 */
    private static final long TICK_MS = 4000;
    private static final long FUNDING = 1_000_000L;

    private final ChainService chain;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicLong flip = new AtomicLong();
    private final SecureRandom rnd = new SecureRandom();

    /** 两个演示账户（仅地址参与转账；StateMachine 只校验 nonce，无需签名，同购买扣款路径）。 */
    private final byte[] alice = CryptoUtils.toAddress(CryptoUtils.generateKeyPair().getPublic());
    private final byte[] bob = CryptoUtils.toAddress(CryptoUtils.generateKeyPair().getPublic());

    private ScheduledExecutorService scheduler;

    public DemoTrafficService(ChainService chain) {
        this.chain = chain;
    }

    @PostConstruct
    void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "demo-traffic");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeTick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        LOG.info("演示流量发生器就绪（默认关闭，POST /chain/demo-traffic?enabled=true 开启）");
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 开关演示流量。
     *
     * @param on 是否开启
     * @return 当前状态
     */
    public boolean setEnabled(boolean on) {
        boolean prev = enabled.getAndSet(on);
        if (prev != on) {
            LOG.info("演示流量{}", on ? "开启：每 " + TICK_MS + "ms 提交 1~2 笔真实互转交易" : "关闭");
        }
        return on;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    private void safeTick() {
        if (!enabled.get()) {
            return;
        }
        try {
            tickOnce();
        } catch (Exception e) {
            LOG.warn("演示流量提交失败（忽略，下个周期重试）：{}", e.getMessage());
        }
    }

    /** 单次演示流量：提交 1~2 笔小额互转（独立可测，便于确定性断言）。 */
    public void tickOnce() {
        int n = 1 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) {
            transferOnce();
        }
    }

    private synchronized void transferOnce() {
        if (chain.balanceOf(alice) < 100) {
            chain.fund(alice, FUNDING);
        }
        if (chain.balanceOf(bob) < 100) {
            chain.fund(bob, FUNDING);
        }
        boolean forward = flip.getAndIncrement() % 2 == 0;
        byte[] from = forward ? alice : bob;
        byte[] to = forward ? bob : alice;
        long amount = 1 + rnd.nextInt(50);
        Transaction tx = Transaction.create(TxType.TOKEN_TRANSFER, from, to, chain.nonceOf(from),
                new TokenTransferPayload(amount).encode(), System.currentTimeMillis());
        chain.applyTx(tx);
    }
}
