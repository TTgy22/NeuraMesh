package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuramesh.api.dto.BlockInfoDTO;
import com.neuramesh.api.dto.NodeStatusDTO;
import com.neuramesh.api.dto.PurchaseReceiptDTO;
import com.neuramesh.api.dto.RegisterRequest;
import com.neuramesh.api.dto.TaskStatusDTO;
import com.neuramesh.api.dto.TaskSubmitDTO;
import com.neuramesh.api.dto.TokenResponse;
import com.neuramesh.api.security.UserPrincipal;
import com.neuramesh.api.service.AuthService;
import com.neuramesh.api.service.ChainService;
import com.neuramesh.api.service.NodeService;
import com.neuramesh.api.service.ResourceGroupService;
import com.neuramesh.api.service.UserService;
import com.neuramesh.api.service.VendorService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实端到端：注册 → 登录 → 节点上链 → 购买资源组 → 提交任务，全流程经
 * <strong>真实共识管线</strong>（TxPool → BFT 出块 → BlockStore → 状态机），断言链上记录与余额变更。
 */
@SpringBootTest
class RealEndToEndTest {

    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private NodeService nodeService;
    @Autowired private VendorService vendorService;
    @Autowired private ResourceGroupService groupService;
    @Autowired private ChainService chain;

    @Test
    @Timeout(30)
    void fullDemoScenario() {
        int heightBefore = chain.blockHeight();

        // 1. 厂商注册（生成密钥对 + 初始注资）→ 登录验证
        String username = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse reg = authService.register(new RegisterRequest(username, "secret123", "VENDOR"));
        TokenResponse login = authService.login(username, "secret123");
        assertThat(login.userId()).isEqualTo(reg.userId());
        assertThat(userService.balance(reg.userId())).isEqualTo(5_000_000L);

        // 2. 节点注册（指定资源组）：经真实管线产生 NODE_REGISTER + WEIGHT_UPDATE 两笔交易上链
        NodeStatusDTO node = nodeService.register("RTX-4090", "north-china-qingdao");
        assertThat(node.nodeId()).startsWith("0x");
        assertThat(node.totalWeight()).isGreaterThan(0);
        assertThat(node.fingerprint()).isNotBlank();
        assertThat(chain.blockHeight()).isGreaterThanOrEqualTo(heightBefore + 2);

        // 2b. 未指定分组的节点兜底进入 general-purpose 默认组（修复"无合格节点"）
        NodeStatusDTO bare = nodeService.register("Jetson-Orin");
        assertThat(chain.state().resourceGroups()
                .membershipOf(bare.nodeId().substring(2)).groupId())
                .isEqualTo("general-purpose");

        // 3. 厂商购买资源组（链上 TOKEN_TRANSFER 扣款）
        PurchaseReceiptDTO receipt = groupService.buy("north-china-qingdao",
                new UserPrincipal(reg.userId(), reg.username(), reg.role(), reg.address()), 2);
        assertThat(receipt.settleTxId()).startsWith("0x");
        assertThat(userService.balance(reg.userId())).isEqualTo(5_000_000L - receipt.totalCost());
        assertThat(chain.txLifecycle(receipt.settleTxId())).isEqualTo(ChainService.TX_EXECUTED);

        // 4. 厂商提交任务 → 真实 TASK_SETTLE 上链结算到节点
        TaskStatusDTO task = vendorService.submit(
                new TaskSubmitDTO(username, "image-classification", 30_000L));
        assertThat(task.status()).isEqualTo("SETTLED");
        assertThat(task.settleTxId()).startsWith("0x");

        // 4b. 组内任务：已购资源组内按权重自动分配（修复"无合格节点"后应 SETTLED）
        TaskStatusDTO groupTask = groupService.allocateTask(
                "north-china-qingdao", username, "llm-inference", 20_000L);
        assertThat(groupTask.status()).isEqualTo("SETTLED");
        assertThat(groupTask.assignedNodes()).contains(node.nodeId());
        assertThat(chain.txLifecycle(groupTask.settleTxId())).isEqualTo(ChainService.TX_EXECUTED);

        // 5. 链上验证：结算交易可查、已执行、节点收益增加
        assertThat(chain.findTx(task.settleTxId())).isNotNull();
        assertThat(chain.txLifecycle(task.settleTxId())).isEqualTo(ChainService.TX_EXECUTED);
        NodeStatusDTO after = nodeService.status(node.nodeId());
        assertThat(after.totalEarned()).isGreaterThan(0);

        // 6. 区块浏览器：从真实 BlockStore 读取，哈希为 64 位 hex，链高度递增
        List<BlockInfoDTO> blocks = chain.latestBlocks(20);
        assertThat(blocks).isNotEmpty();
        assertThat(blocks.get(0).hash()).hasSize(64);
        assertThat(chain.blockHeight()).isGreaterThan(heightBefore);
    }
}
