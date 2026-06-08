package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import com.neuramesh.api.dto.LoginRequest;
import com.neuramesh.api.dto.RegisterRequest;
import com.neuramesh.api.dto.TokenResponse;
import com.neuramesh.api.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 REST API：注册 / 登录 / 刷新令牌。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@RequestBody RegisterRequest req) {
        try {
            return ApiResponse.ok(authService.register(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginRequest req) {
        try {
            return ApiResponse.ok(authService.login(req.username(), req.password()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        try {
            String token = body == null ? null : body.get("refreshToken");
            return ApiResponse.ok(authService.refresh(token));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }
}
