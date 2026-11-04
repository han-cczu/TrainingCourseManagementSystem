package com.example.tcms.user.dto;

import com.example.tcms.common.ValidationPatterns;
import com.example.tcms.user.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员创建用户（可指定角色）。 */
public record CreateUserRequest(
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
        String department,

        @NotNull(message = "角色不能为空")
        Role role) {
}
