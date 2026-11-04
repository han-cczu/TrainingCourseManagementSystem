package com.example.tcms.auth;

import com.example.tcms.user.Role;

/** 当前登录用户。控制器中通过 {@code @AuthenticationPrincipal CurrentUser} 获取。 */
public record CurrentUser(long id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
