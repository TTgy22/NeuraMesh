package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.service.ChainService;
import com.neuramesh.api.service.NodeService;
import com.neuramesh.api.service.ResourceGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 组任务模拟计算：simulateMs &gt; 0 时先 RUNNING（节点"执行推理"），到点真实上链结算 → SETTLED；
 * simulateMs = 0 保持即时结算（既有调用方语义不变）。
 */
@SpringBootTest
class GroupTaskSimulationTest {

    @Autowired private ResourceGroupService groupService;
    @Autowired private NodeService nodeService;
    @Autowired private ChainService chain;

    @Test
    @Timeout(30)
    void simulated_task_runs_then_settles_on_chain() throws InterruptedException {
        nodeService.register("RTX-3080", "general-purpose");

        // 1) 模拟计算路径：先 RUNNING
        TaskStatusDTO running = groupService.allocateTask(
                "general-purpose", "sim-vendor", "ocr", 10_000L, 300);
        assertThat(running.status()).isEqualTo("RUNNING");
        assertThat(running.settleTxId()).isNull();
        assertThat(running.assignedNodes()).isNotEmpty();

        // 2) 到点后真实上链：轮询至 SETTLED
        TaskStatusDTO done = running;
        for (int i = 0; i < 50 && "RUNNING".equals(done.status()); i++) {
            Thread.sleep(200);
            done = groupService.groupTask(running.taskId());
        }
        assertThat(done.status()).isEqualTo("SETTLED");
        assertThat(done.settleTxId()).startsWith("0x");
        assertThat(chain.txLifecycle(done.settleTxId())).isEqualTo(ChainService.TX_EXECUTED);

        // 3) 即时路径（simulateMs=0）：同步 SETTLED，旧语义不变
        TaskStatusDTO instant = groupService.allocateTask(
                "general-purpose", "sim-vendor", "ocr", 5_000L, 0);
        assertThat(instant.status()).isEqualTo("SETTLED");
        assertThat(groupService.groupTask(instant.taskId()).status()).isEqualTo("SETTLED");

        // 4) 历史任务注册表（前端切页/刷新的权威数据源）：新在前，包含上述任务
        assertThat(groupService.allGroupTasks())
                .extracting(TaskStatusDTO::taskId)
                .contains(running.taskId(), instant.taskId());
        assertThat(groupService.allGroupTasks().get(0).taskId()).isEqualTo(instant.taskId());
    }
}
