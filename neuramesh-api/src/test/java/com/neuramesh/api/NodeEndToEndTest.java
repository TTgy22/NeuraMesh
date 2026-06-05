package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.dto.NodeRegisterRequest;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.service.NodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 端到端：注册节点 → 状态机执行 NODE_REGISTER + WEIGHT_UPDATE → 返回 NodeID 与状态。
 */
@SpringBootTest
class NodeEndToEndTest {

    @Autowired
    private NodeService nodeService;

    @Test
    @Timeout(30)
    void register_then_query_status() {
        NodeStatusDTO registered = nodeService.register("Jetson-Orin-NX");
        assertThat(registered).isNotNull();
        assertThat(registered.nodeId()).startsWith("0x");
        assertThat(registered.online()).isTrue();
        assertThat(registered.totalWeight()).isGreaterThan(0);
        assertThat(registered.deviceModel()).isEqualTo("Jetson-Orin-NX");

        NodeStatusDTO queried = nodeService.status(registered.nodeId());
        assertThat(queried).isNotNull();
        assertThat(queried.nodeId()).isEqualTo(registered.nodeId());

        // 启停切换
        assertThat(nodeService.stop(registered.nodeId()).online()).isFalse();
        assertThat(nodeService.start(registered.nodeId()).online()).isTrue();
    }
}
