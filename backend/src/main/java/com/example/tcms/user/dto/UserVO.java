package com.example.tcms.user.dto;

import java.time.LocalDateTime;

import com.example.tcms.user.Role;
import com.example.tcms.user.User;

/** 用户信息（不含密码哈希）。 */
public record UserVO(
        Long id,
        String username,
        String realName,
        String department,
        Role role,
        boolean enabled,
        LocalDateTime createdAt) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getDepartment(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
