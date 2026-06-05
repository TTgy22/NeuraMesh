package com.neuramesh.api.service;

import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.dto.TaskSubmitDTO;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.core.Transaction;
import com.neuramesh.core.TxType;
import com.neuramesh.vm.payload.TaskSettlePayload;
import com.neuramesh.vm.payload.TaskSettlePayload.Allocation;
import com.neuramesh.vm.state.NodeState;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 厂商服务：封装任务提交 → 按节点权重结算（TASK_SETTLE）→ 收集结果。
 *
 * <p>演示用：每个厂商标签首次提交时自动注资 {@link #INITIAL_FUNDING}；任务按在线节点权重比例分配预算。
 */
@Service
public class VendorService {

    private static final Logger LOG = LoggerFactory.getLogger(VendorService.class);
    private static final long INITIAL_FUNDING = 1_000_000L;

    private final ChainService chain;
    private final NodeService nodeService;

    private final Map<String, KeyPair> vendorKeys = new ConcurrentHashMap<>();
    private final Map<String, TaskStatusDTO> tasks = new ConcurrentHashMap<>();
    private final AtomicLong taskSeq = new AtomicLong(1);

    public VendorService(ChainService chain, NodeService nodeService) {
        this.chain = chain;
        this.nodeService = nodeService;
    }

    /**
     * 提交任务并链上结算。
     *
     * @param dto 任务提交参数
     * @return 任务状态
     */
    public TaskStatusDTO submit(TaskSubmitDTO dto) {
        String label = (dto.vendorId() == null || dto.vendorId().isBlank())
                ? "vendor-1" : dto.vendorId();
        long budget = dto.budget() > 0 ? dto.budget() : 30_000L;
        String taskType = (dto.taskType() == null || dto.taskType().isBlank())
                ? "image-classification" : dto.taskType();

        KeyPair kp = vendorKeys.computeIfAbsent(label, k -> CryptoUtils.generateKeyPair());
        byte[] vendorAddr = CryptoUtils.toAddress(kp.getPublic());
        if (chain.balanceOf(vendorAddr) < budget) {
            chain.fund(vendorAddr, INITIAL_FUNDING);
        }

        List<byte[]> nodes = nodeService.eligibleNodes();
        String taskId = "task-" + taskSeq.getAndIncrement();
        if (nodes.isEmpty()) {
            TaskStatusDTO failed = new TaskStatusDTO(taskId, taskType, "FAILED", budget,
                    null, List.of(), null);
            tasks.put(taskId, failed);
            LOG.warn("任务 {} 无可用节点，结算失败", taskId);
            return failed;
        }

        List<Allocation> allocations = new ArrayList<>();
        List<String> assigned = new ArrayList<>();
        for (byte[] nodeId : nodes) {
            NodeState ns = chain.state().getNode(nodeId);
            long w = Math.max(1, Math.round(ns.getTotalWeight()));
            allocations.add(new Allocation(nodeId, w));
            assigned.add("0x" + CryptoUtils.toHex(nodeId));
        }

        TaskSettlePayload payload = new TaskSettlePayload(
                taskId.getBytes(StandardCharsets.UTF_8), budget, allocations);
        long nonce = chain.nonceOf(vendorAddr);
        Transaction tx = Transaction.create(TxType.TASK_SETTLE, vendorAddr, vendorAddr, nonce,
                payload.encode(), System.currentTimeMillis());
        tx = tx.withSignature(CryptoUtils.sign(tx.signingBytes(), kp.getPrivate()));
        chain.applyTx(tx);

        TaskStatusDTO status = new TaskStatusDTO(taskId, taskType, "SETTLED", budget,
                "0x" + CryptoUtils.toHex(tx.getTxId()), assigned,
                "https://placeholder.co/400x300?text=" + taskId);
        tasks.put(taskId, status);
        LOG.info("任务 {} 结算完成，分配 {} 个节点，预算 {}", taskId, nodes.size(), budget);
        return status;
    }

    public TaskStatusDTO status(String taskId) {
        return tasks.get(taskId);
    }

    public TaskStatusDTO result(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 厂商余额（按标签）。
     *
     * @param label 厂商标签
     * @return 余额
     */
    public long balance(String label) {
        KeyPair kp = vendorKeys.get(label);
        if (kp == null) {
            return 0;
        }
        return chain.balanceOf(CryptoUtils.toAddress(kp.getPublic()));
    }
}
