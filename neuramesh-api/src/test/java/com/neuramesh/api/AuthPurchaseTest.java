package com.neuramesh.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuramesh.api.dto.PurchaseReceiptDTO;
import com.neuramesh.api.dto.RegisterRequest;
import com.neuramesh.api.dto.TokenResponse;
import com.neuramesh.api.security.UserPrincipal;
import com.neuramesh.api.service.AuthService;
import com.neuramesh.api.service.ResourceGroupService;
import com.neuramesh.api.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 端到端：注册 → 登录 → 查询余额 → 购买资源组（链上扣款）→ 我的资源组。
 */
@SpringBootTest
class AuthPurchaseTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private ResourceGroupService groupService;

    private UserPrincipal principalOf(TokenResponse t) {
        return new UserPrincipal(t.userId(), t.username(), t.role(), t.address());
    }

    @Test
    @Timeout(30)
    void register_login_balance_buy_flow() {
        String username = "vendor-" + UUID.randomUUID().toString().substring(0, 8);

        // 注册（生成密钥对 + 初始注资）
        TokenResponse reg = authService.register(new RegisterRequest(username, "secret123", "VENDOR"));
        assertThat(reg.accessToken()).isNotBlank();
        assertThat(reg.address()).startsWith("0x");

        // 登录
        TokenResponse login = authService.login(username, "secret123");
        assertThat(login.userId()).isEqualTo(reg.userId());

        // 查询余额（厂商初始注资 5,000,000）
        long balance = userService.balance(reg.userId());
        assertThat(balance).isEqualTo(5_000_000L);

        // 购买资源组（north-china-qingdao：20,000/h × 2h = 40,000）
        PurchaseReceiptDTO receipt = groupService.buy("north-china-qingdao", principalOf(reg), 2);
        assertThat(receipt.totalCost()).isEqualTo(40_000L);
        assertThat(receipt.settleTxId()).startsWith("0x");
        assertThat(receipt.groupPrivateKey()).isNotBlank();
        assertThat(receipt.remainingBalance()).isEqualTo(5_000_000L - 40_000L);

        // 余额已扣
        assertThat(userService.balance(reg.userId())).isEqualTo(4_960_000L);

        // 我的资源组
        List<Map<String, Object>> mine = groupService.myGroups(reg.userId());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("groupId")).isEqualTo("north-china-qingdao");
        assertThat(mine.get(0).get("active")).isEqualTo(true);
    }

    @Test
    @Timeout(30)
    void duplicate_username_rejected() {
        String username = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        authService.register(new RegisterRequest(username, "secret123", "VENDOR"));
        assertThatThrownBy(() -> authService.register(new RegisterRequest(username, "secret123", "VENDOR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(30)
    void wrong_password_rejected() {
        String username = "wp-" + UUID.randomUUID().toString().substring(0, 8);
        authService.register(new RegisterRequest(username, "secret123", "VENDOR"));
        assertThatThrownBy(() -> authService.login(username, "wrongpass"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(30)
    void buy_insufficient_balance_rejected() {
        String username = "poor-" + UUID.randomUUID().toString().substring(0, 8);
        // NODE_OPERATOR 无初始注资 → 余额 0，购买应失败
        TokenResponse reg = authService.register(new RegisterRequest(username, "secret123", "NODE_OPERATOR"));
        assertThat(userService.balance(reg.userId())).isZero();
        assertThatThrownBy(() -> groupService.buy("north-china-qingdao", principalOf(reg), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
