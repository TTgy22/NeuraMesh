package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.security.UserPrincipal;
import com.neuramesh.api.service.UserService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 REST API（需 JWT 认证）：当前用户信息与余额。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ApiResponse.error(401, "未认证");
        }
        Map<String, Object> profile = userService.profile(principal.userId());
        if (profile == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.ok(profile);
    }

    @GetMapping("/balance")
    public ApiResponse<Map<String, Object>> balance(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ApiResponse.error(401, "未认证");
        }
        return ApiResponse.ok(Map.of("balance", userService.balance(principal.userId())));
    }
}
