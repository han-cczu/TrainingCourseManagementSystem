package com.example.tcms.auth.dto;

import com.example.tcms.common.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 员工自助注册。没有 role 字段：注册用户固定为 EMPLOYEE，请求中多传的 role 会被忽略。 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = ValidationPatterns.USERNAME, message = ValidationPatterns.USERNAME_MESSAGE)
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名最多 32 个字符")
        String realName,

        @Size(max = 64, message = "部门最多 64 个字符")
        String department) {
}
