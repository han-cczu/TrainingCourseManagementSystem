package com.example.tcms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/** 初始管理员账号（技术设计 §4.2），生产环境来自 K8s Secret。 */
@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(@NotBlank String username, @NotBlank String password) {
}
