package com.example.tcms.support;

import org.springframework.jdbc.core.JdbcTemplate;

import com.example.tcms.auth.JwtService;
import com.example.tcms.user.Role;
import com.example.tcms.user.User;
import com.example.tcms.user.UserService;

/** 测试数据工具：清库、造用户、签发 token。通过 @Import 注册为 Bean。 */
public class TestDataHelper {

    /** 测试用户的统一密码（满足密码规则） */
    public static final String PASSWORD = "Passw0rd123";

    private final JdbcTemplate jdbc;
    private final UserService userService;
    private final JwtService jwtService;

    public TestDataHelper(JdbcTemplate jdbc, UserService userService, JwtService jwtService) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /** 按外键顺序清空业务表（不用 TRUNCATE + 关外键检查：连接池中 SET 只对单个连接生效） */
    public void truncateAll() {
        jdbc.update("DELETE FROM enrollment");
        jdbc.update("DELETE FROM course");
        jdbc.update("DELETE FROM sys_user");
    }

    public User createUser(String username, Role role) {
        return userService.create(username, PASSWORD, "测试-" + username, "研发部", role);
    }

    public User createEmployee(String username) {
        return createUser(username, Role.EMPLOYEE);
    }

    public User createAdmin(String username) {
        return createUser(username, Role.ADMIN);
    }

    public void disable(User user) {
        jdbc.update("UPDATE sys_user SET enabled = FALSE WHERE id = ?", user.getId());
    }

    public String bearer(User user) {
        return "Bearer " + jwtService.issue(user).token();
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
