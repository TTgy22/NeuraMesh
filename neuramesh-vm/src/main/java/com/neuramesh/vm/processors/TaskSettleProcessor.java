package com.neuramesh.vm.processors;

import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.TransactionProcessor;
import com.neuramesh.vm.exception.VMException;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.state.AccountState;
import com.neuramesh.vm.state.GlobalState;
import com.neuramesh.vm.state.NodeState;
import java.util.List;

/**
 * TASK_SETTLE 处理器：厂商（tx.from）支付 totalFee，按各节点权重比例原子分配。
 *
 * <p>分配采用整数除法 {@code share_i = totalFee * weight_i / totalWeight}，余数补给权重最大的节点，
 * 保证 {@code 总分配额 == totalFee} 精确守恒（零误差）。先扣厂商余额（不足即失败，整体回滚）。
 */
public final class TaskSettleProcessor implements TransactionProcessor {

    @Override
    public void process(Transaction tx, GlobalState state) {
        TaskSettlePayload p = TaskSettlePayload.decode(tx.getPayload());
        List<TaskSettlePayload.Allocation> allocs = p.allocations();
        if (allocs.isEmpty()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "任务结算无分配目标");
        }

        long totalWeight = 0;
        for (TaskSettlePayload.Allocation a : allocs) {
            totalWeight += a.weight();
        }
        if (totalWeight <= 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "总权重必须为正");
        }

        // 先扣厂商余额（不足则抛出，状态机回滚）
        AccountState payer = state.getOrCreateAccount(tx.getFrom());
        payer.debit(p.totalFee());

        // 整数比例分配，余数补给权重最大者，确保精确守恒
        long distributed = 0;
        int maxIdx = 0;
        for (int i = 0; i < allocs.size(); i++) {
            if (allocs.get(i).weight() > allocs.get(maxIdx).weight()) {
                maxIdx = i;
            }
            long share = Math.floorDiv(
                    Math.multiplyExact(p.totalFee(), allocs.get(i).weight()), totalWeight);
            creditNode(state, allocs.get(i).nodeId(), share);
            distributed += share;
        }
        long remainder = p.totalFee() - distributed;
        if (remainder > 0) {
            creditNode(state, allocs.get(maxIdx).nodeId(), remainder);
        }
    }

    private void creditNode(GlobalState state, byte[] nodeId, long amount) {
        if (amount <= 0) {
            return;
        }
        state.getOrCreateAccount(nodeId).credit(amount);
        NodeState node = state.getNode(nodeId);
        if (node != null) {
            node.addEarned(amount);
        }
    }

    @Override
    public TxType getType() {
        return TxType.TASK_SETTLE;
    }
}
