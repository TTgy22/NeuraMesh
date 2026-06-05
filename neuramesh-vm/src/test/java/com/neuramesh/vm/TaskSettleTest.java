package com.neuramesh.vm;

import static com.neuramesh.vm.TestVmSupport.addr;
import static com.neuramesh.vm.TestVmSupport.tx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.core.TxType;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.payload.TaskSettlePayload.Allocation;
import com.neuramesh.vm.state.GlobalState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskSettleTest {

    private StateMachine sm() {
        return StateMachine.standard(TestVmSupport.validators(4).set());
    }

    @Test
    @DisplayName("厂商 100、费用 30、3 节点等权 → 厂商 70，各节点 10")
    void settle_equal_split() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 100);

        TaskSettlePayload p = new TaskSettlePayload("task-1".getBytes(), 30, List.of(
                new Allocation(addr(2), 1),
                new Allocation(addr(3), 1),
                new Allocation(addr(4), 1)));
        sm.apply(tx(TxType.TASK_SETTLE, addr(1), addr(1), 0, p.encode()), state);

        assertThat(state.getAccount(addr(1)).getBalance()).isEqualTo(70);
        assertThat(state.getAccount(addr(2)).getBalance()).isEqualTo(10);
        assertThat(state.getAccount(addr(3)).getBalance()).isEqualTo(10);
        assertThat(state.getAccount(addr(4)).getBalance()).isEqualTo(10);
        assertThat(state.totalBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("百万级分配精确守恒，误差 ≤ 0.01%")
    void settle_million_precision() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        long fee = 1_000_000L;
        state.credit(addr(1), fee);

        TaskSettlePayload p = new TaskSettlePayload("task-big".getBytes(), fee, List.of(
                new Allocation(addr(2), 1),
                new Allocation(addr(3), 1),
                new Allocation(addr(4), 1)));
        sm.apply(tx(TxType.TASK_SETTLE, addr(1), addr(1), 0, p.encode()), state);

        long n2 = state.getAccount(addr(2)).getBalance();
        long n3 = state.getAccount(addr(3)).getBalance();
        long n4 = state.getAccount(addr(4)).getBalance();
        // 精确守恒：分配总额等于费用，厂商清零
        assertThat(n2 + n3 + n4).isEqualTo(fee);
        assertThat(state.getAccount(addr(1)).getBalance()).isZero();
        // 每份与理想均分的相对误差 ≤ 0.01%
        double ideal = fee / 3.0;
        for (long v : new long[] {n2, n3, n4}) {
            assertThat(Math.abs(v - ideal) / ideal).isLessThanOrEqualTo(0.0001);
        }
    }

    @Test
    @DisplayName("加权分配按权重比例")
    void settle_weighted_split() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 100);
        TaskSettlePayload p = new TaskSettlePayload("t".getBytes(), 100, List.of(
                new Allocation(addr(2), 1),
                new Allocation(addr(3), 4)));
        sm.apply(tx(TxType.TASK_SETTLE, addr(1), addr(1), 0, p.encode()), state);
        // 权重 1:4 → 20 : 80
        assertThat(state.getAccount(addr(2)).getBalance()).isEqualTo(20);
        assertThat(state.getAccount(addr(3)).getBalance()).isEqualTo(80);
    }

    @Test
    @DisplayName("厂商余额不足 → 失败回滚")
    void settle_insufficient_balance() {
        StateMachine sm = sm();
        GlobalState state = new GlobalState();
        state.credit(addr(1), 10);
        TaskSettlePayload p = new TaskSettlePayload("t".getBytes(), 30, List.of(
                new Allocation(addr(2), 1)));
        assertThatThrownBy(() -> sm.apply(tx(TxType.TASK_SETTLE, addr(1), addr(1), 0, p.encode()), state))
                .isInstanceOf(VMException.class)
                .extracting(e -> ((VMException) e).getKind())
                .isEqualTo(VMException.Kind.INSUFFICIENT_BALANCE);
        assertThat(state.getAccount(addr(1)).getBalance()).isEqualTo(10);
        assertThat(state.getAccount(addr(2))).isNull();
    }
}
