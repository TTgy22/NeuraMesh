package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.service.ChainService;
import com.neuramesh.api.service.DemoTrafficService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 演示流量发生器：默认关闭；开关状态可切换；单次 tick 经真实管线产生新区块。
 */
@SpringBootTest
class DemoTrafficTest {

    @Autowired private DemoTrafficService demoTraffic;
    @Autowired private ChainService chain;

    @Test
    @Timeout(30)
    void toggle_and_tick_produces_real_blocks() {
        // 默认关闭
        assertThat(demoTraffic.isEnabled()).isFalse();

        // 开关可切换
        assertThat(demoTraffic.setEnabled(true)).isTrue();
        assertThat(demoTraffic.isEnabled()).isTrue();

        // 单次 tick：真实交易上链，区块高度增长
        int before = chain.blockHeight();
        demoTraffic.tickOnce();
        assertThat(chain.blockHeight()).isGreaterThan(before);

        demoTraffic.setEnabled(false);
        assertThat(demoTraffic.isEnabled()).isFalse();
    }
}
