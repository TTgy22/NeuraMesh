package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.controller.ChainController;
import com.neuramesh.api.controller.NodeController;
import com.neuramesh.api.controller.VendorController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文加载与 Controller Bean 初始化验证。
 */
@SpringBootTest
class ApiApplicationTest {

    @Autowired
    private NodeController nodeController;

    @Autowired
    private VendorController vendorController;

    @Autowired
    private ChainController chainController;

    @Test
    void contextLoads() {
        assertThat(nodeController).isNotNull();
        assertThat(vendorController).isNotNull();
        assertThat(chainController).isNotNull();
    }
}
