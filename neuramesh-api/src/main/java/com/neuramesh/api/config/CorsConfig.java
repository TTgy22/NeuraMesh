package com.neuramesh.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置：允许前端（Electron 客户端 / React 控制台）跨域访问。
 * 允许的来源从 {@code neuramesh.cors.allowed-origins} 读取。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${neuramesh.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
