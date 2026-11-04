package com.example.tcms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求只校验非空，不暴露用户名/密码规则。 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") @Size(max = 64) String username,
        @NotBlank(message = "密码不能为空") @Size(max = 128) String password) {
}
