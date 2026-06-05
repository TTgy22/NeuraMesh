package com.neuramesh.vm;

import static com.neuramesh.vm.TestVmSupport.addr;
import static com.neuramesh.vm.TestVmSupport.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.TxType;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.TokenTransferPayload;
import com.neuramesh.vm.state.GlobalState;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TokenTransferTest {

    private StateMachine sm() {
        return StateMachine.standard(TestVmSupport.validators(4).set());
    }

    @Test
    @DisplayName("余额充足转账成功，双方余额正确")
    void transfer_success() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 100);

        sm.apply(tx(TxType.TOKEN_TRANSFER, addr(1), addr(2), 0,
                new TokenTransferPayload(30).encode()), state);

        assertThat(state.getAccount(addr(1)).getBalance()).isEqualTo(70);
        assertThat(state.getAccount(addr(2)).getBalance()).isEqualTo(30);
        assertThat(state.getAccount(addr(1)).getNonce()).isEqualTo(1);
        assertThat(state.totalBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("余额不足转账失败并回滚")
    void insufficient_balance_rolls_back() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 10);

        assertThatThrownBy(() -> sm.apply(tx(TxType.TOKEN_TRANSFER, addr(1), addr(2), 0,
                new TokenTransferPayload(50).encode()), state))
                .isInstanceOf(VMException.class)
                .extracting(e -> ((VMException) e).getKind())
                .isEqualTo(VMException.Kind.INSUFFICIENT_BALANCE);

        assertThat(state.getAccount(addr(1)).getBalance()).isEqualTo(10);
        assertThat(state.getAccount(addr(1)).getNonce()).isZero();
        assertThat(state.totalBalance()).isEqualTo(10);
    }

    @Test
    @DisplayName("nonce 重复被拒绝")
    void duplicate_nonce_rejected() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 100);
        sm.apply(tx(TxType.TOKEN_TRANSFER, addr(1), addr(2), 0,
                new TokenTransferPayload(10).encode()), state);
        // 再次用 nonce=0 提交
        assertThatThrownBy(() -> sm.apply(tx(TxType.TOKEN_TRANSFER, addr(1), addr(2), 0,
                new TokenTransferPayload(10).encode()), state))
                .isInstanceOf(VMException.class)
                .extracting(e -> ((VMException) e).getKind())
                .isEqualTo(VMException.Kind.DUPLICATE_NONCE);
    }

    @Test
    @Timeout(30)
    @DisplayName("并发双花防护：10 线程争抢同一笔余额，仅 1 笔成功")
    void concurrent_no_double_spend() throws InterruptedException {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 100);
        
        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    start.await();
                    sm.apply(tx(TxType.TOKEN_TRANSFER, addr(1), addr(2), 0,
                            new TokenTransferPayload(100).encode()), state);
                    success.incrementAndGet();
                } catch (Exception ignore) {
                    // 预期大多数失败（nonce/余额）
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        exec.shutdownNow();

        assertThat(success.get()).as("仅应有 1 笔成功").isEqualTo(1);
        assertThat(state.getAccount(addr(1)).getBalance()).isZero();
        assertThat(state.getAccount(addr(2)).getBalance()).isEqualTo(100);
        assertThat(state.totalBalance()).isEqualTo(100);
    }
}
