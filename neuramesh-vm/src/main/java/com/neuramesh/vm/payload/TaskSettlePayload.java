package com.neuramesh.vm.payload;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.exception.VMException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TASK_SETTLE 负载：任务 ID、总费用、按节点的权重分配列表，以及可选的资源组 id（P5）。
 *
 * <p>分配按各节点 weight 占比进行；整数除法的余数补给权重最大的节点，保证总额精确守恒。
 *
 * <p>资源组（P5）：当 {@code allocations} 为空且 {@code resourceGroupId} 非空时，处理器会在组内
 * 按节点权重自动解析分配；否则沿用 P3 的显式分配列表。{@code resourceGroupId} 为空串表示不绑定组。
 */
public record TaskSettlePayload(byte[] taskId, long totalFee, List<Allocation> allocations,
                                String resourceGroupId) {

    /**
     * 单节点分配权重。
     *
     * @param nodeId 节点 20 字节地址
     * @param weight 该节点的相对权重（&gt; 0）
     */
    public record Allocation(byte[] nodeId, long weight) {
        public Allocation {
            if (nodeId == null || nodeId.length != CryptoUtils.ADDRESS_LENGTH) {
                throw new VMException(VMException.Kind.INVALID_PAYLOAD,
                        "allocation nodeId 长度需为 " + CryptoUtils.ADDRESS_LENGTH);
            }
            if (weight <= 0) {
                throw new VMException(VMException.Kind.INVALID_PAYLOAD, "allocation weight 必须为正");
            }
            nodeId = nodeId.clone();
        }

        @Override
        public byte[] nodeId() {
            return nodeId.clone();
        }
    }

    public TaskSettlePayload {
        if (taskId == null || taskId.length == 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "taskId 不可为空");
        }
        if (totalFee < 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "totalFee 不可为负");
        }
        taskId = taskId.clone();
        allocations = List.copyOf(allocations);
        resourceGroupId = resourceGroupId == null ? "" : resourceGroupId;
    }

    /** 向后兼容构造器（P3）：不绑定资源组。 */
    public TaskSettlePayload(byte[] taskId, long totalFee, List<Allocation> allocations) {
        this(taskId, totalFee, allocations, "");
    }

    @Override
    public byte[] taskId() {
        return taskId.clone();
    }

    @Override
    public List<Allocation> allocations() {
        return Collections.unmodifiableList(allocations);
    }

    public byte[] encode() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(taskId.length);
            out.write(taskId);
            out.writeLong(totalFee);
            out.writeInt(allocations.size());
            for (Allocation a : allocations) {
                out.write(a.nodeId());
                out.writeLong(a.weight());
            }
            out.writeUTF(resourceGroupId);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "TASK_SETTLE 编码失败", e);
        }
    }

    public static TaskSettlePayload decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "TASK_SETTLE 负载为空");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int tlen = in.readInt();
            if (tlen <= 0 || tlen > 1024) {
                throw new VMException(VMException.Kind.INVALID_PAYLOAD, "taskId 长度非法: " + tlen);
            }
            byte[] taskId = new byte[tlen];
            in.readFully(taskId);
            long fee = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > 100_000) {
                throw new VMException(VMException.Kind.INVALID_PAYLOAD, "分配数量非法: " + count);
            }
            List<Allocation> allocs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte[] nid = new byte[CryptoUtils.ADDRESS_LENGTH];
                in.readFully(nid);
                long w = in.readLong();
                allocs.add(new Allocation(nid, w));
            }
            String groupId = in.available() > 0 ? in.readUTF() : "";
            return new TaskSettlePayload(taskId, fee, allocs, groupId);
        } catch (IOException e) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "TASK_SETTLE 解码失败", e);
        }
    }
}
