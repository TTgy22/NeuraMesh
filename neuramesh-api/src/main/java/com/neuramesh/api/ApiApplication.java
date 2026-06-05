package com.neuramesh.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NeuraMesh API 网关入口（Spring Boot）。
 *
 * <p>对外暴露节点、厂商、链三组 REST API；内部封装 P0–P3 的 StateMachine、DeviceBenchmark、
 * BlockStore 等能力。默认端口 8080，CORS 允许前端（Electron 客户端 / React 控制台）访问。
 */
@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
