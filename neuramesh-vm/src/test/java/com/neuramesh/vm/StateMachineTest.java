package com.neuramesh.vm;

import static com.neuramesh.vm.TestVmSupport.tx;
import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.payload.TaskSettlePayload.Allocation;
import com.neuramesh.vm.payload.TokenTransferPayload;
import com.neuramesh.vm.state.GlobalState;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class StateMachineTest {

    private static byte[] acct(int i) {
        byte[] a = new byte[CryptoUtils.ADDRESS_LENGTH];
        a[0] = (byte) i;
        a[1] = (byte) (i >> 8);
        return a;
    }

    @Test
    @Timeout(60)
    @DisplayName("1000 笔混合交易：状态根变化 + 余额守恒")
    void thousand_mixed_transactions_conserve_balance() {
        StateMachine sm = StateMachine.standard(TestVmSupport.validators(4).set());
        GlobalState state = new GlobalState();

        final int accounts = 8;
        final long perAccount = 100_000L;
        long initialTotal = 0;
        for (int i = 0; i < accounts; i++) {
            state.credit(acct(i), perAccount);
            initialTotal += perAccount;
        }
        long[] nonces = new long[accounts];

        byte[] rootStart = state.commit();
        Set<String> distinctRoots = new HashSet<>();
        distinctRoots.add(CryptoUtils.toHex(rootStart));

        Random rnd = new Random(2026L);
        int success = 0;
        for (int i = 0; i < 1000; i++) {
            int from = rnd.nextInt(accounts);
            boolean settle = rnd.nextInt(100) < 30;
            try {
                byte[] root;
                if (settle && state.getAccount(acct(from)).getBalance() >= 3) {
                    int a = (from + 1) % accounts;
                    int b = (from + 2) % accounts;
                    int c = (from + 3) % accounts;
                    TaskSettlePayload p = new TaskSettlePayload(("t" + i).getBytes(), 3, List.of(
                            new Allocation(acct(a), 1),
                            new Allocation(acct(b), 1),
                            new Allocation(acct(c), 1)));
                    root = sm.apply(tx(TxType.TASK_SETTLE, acct(from), acct(from), nonces[from],
                            p.encode()), state);
                } else if (state.getAccount(acct(from)).getBalance() >= 1) {
                    int to = (from + 1 + rnd.nextInt(accounts - 1)) % accounts;
                    root = sm.apply(tx(TxType.TOKEN_TRANSFER, acct(from), acct(to), nonces[from],
                            new TokenTransferPayload(1).encode()), state);
                } else {
                    continue;
                }
                nonces[from]++;
                success++;
                distinctRoots.add(CryptoUtils.toHex(root));
            } catch (VMException ignore) {
                // 余额/ nonce 边界，跳过
            }
        }

        // 余额守恒：无手续费，总额恒定
        assertThat(state.totalBalance()).isEqualTo(initialTotal);
        // 状态根确实在演化
        assertThat(success).isGreaterThan(500);
        assertThat(distinctRoots.size()).isGreaterThan(100);
        assertThat(state.commit()).isNotEqualTo(rootStart);
    }

    @Test
    @DisplayName("状态机注册了 4 种交易处理器")
    void four_processors_registered() {
        StateMachine sm = StateMachine.standard(TestVmSupport.validators(4).set());
        assertThat(sm.processorCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("相同状态 commit 确定性一致")
    void commit_deterministic() {
        GlobalState s1 = new GlobalState();
        GlobalState s2 = new GlobalState();
        s1.credit(acct(1), 500);
        s2.credit(acct(1), 500);
        assertThat(s1.commit()).containsExactly(s2.commit());
    }
}