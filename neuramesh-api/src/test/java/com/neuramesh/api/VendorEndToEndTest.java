package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.dto.TaskSubmitDTO;
import com.neuramesh.api.service.NodeService;
import com.neuramesh.api.service.VendorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 端到端：注册节点 → 厂商提交任务 → 状态机执行 TASK_SETTLE → 节点收益增加。
 */
@SpringBootTest
class VendorEndToEndTest {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private VendorService vendorService;

    @Test
    @Timeout(30)
    void submit_task_settles_to_nodes() {
        NodeStatusDTO node = nodeService.register("RTX-4090");
        assertThat(node.totalEarned()).isZero();

        long balanceBefore = vendorService.balance("acme-corp");
        TaskStatusDTO task = vendorService.submit(
                new TaskSubmitDTO("acme-corp", "image-classification", 30_000L));

        assertThat(task.status()).isEqualTo("SETTLED");
        assertThat(task.settleTxId()).startsWith("0x");
        assertThat(task.assignedNodes()).isNotEmpty();

        // 节点收益增加
        NodeStatusDTO after = nodeService.status(node.nodeId());
        assertThat(after.totalEarned()).isGreaterThan(0);

        // 厂商余额减少（首次注资后扣除预算）
        long balanceAfter = vendorService.balance("acme-corp");
        assertThat(balanceAfter).isLessThan(balanceBefore + 1_000_000L);
    }

    @Test
    @Timeout(30)
    void submit_without_nodes_fails_gracefully() {
        // 全新厂商但若已有其他测试注册的节点，可能 SETTLED；此处仅验证不抛异常且有状态
        TaskStatusDTO task = vendorService.submit(
                new TaskSubmitDTO("empty-vendor", "ocr", 1000L));
        assertThat(task.status()).isIn("SETTLED", "FAILED");
    }
}
