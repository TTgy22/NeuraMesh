package com.neuramesh.api.controller;

import com.neuramesh.api.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 根信息接口：避免访问 {@code /} 时出现 Whitelabel 404，返回 API 概览。
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public ApiResponse<Map<String, Object>> home() {
        return ApiResponse.ok(Map.of(
                "service", "neuramesh-api",
                "status", "running",
                "hint", "这是 API 网关。可视化控制台请访问 http://localhost:5173",
                "endpoints", List.of(
                        "GET /chain/stats", "GET /chain/blocks", "GET /chain/tx/{hash}",
                        "GET /node/list", "POST /node/register", "GET /node/{id}/status",
                        "POST /task/submit", "GET /vendor/{id}/balance")));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
