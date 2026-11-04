package com.example.tcms.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.example.tcms.auth.CurrentUser;
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

    /**
     * 直接写库造课程（绕过"开始时间必须在未来"等规则，便于构造已开课/已结束的课程）。
     *
     * @param capacity null 表示不限人数
     * @return 课程 ID
     */
    public long insertCourse(String title, String instructor, LocalDateTime start, LocalDateTime end,
                             Integer capacity, User createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO course (title, instructor, start_time, end_time, capacity, enrolled_count,
                                        created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 0, ?, NOW(), NOW())""", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, instructor);
            ps.setObject(3, start);
            ps.setObject(4, end);
            ps.setObject(5, capacity);
            ps.setLong(6, createdBy.getId());
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public CurrentUser currentUser(User user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getRole());
    }

    public long enrolledCount(long courseId) {
        return count("SELECT enrolled_count FROM course WHERE id = ?", courseId);
    }

    /** 占用名额的报名记录数（ENROLLED + COMPLETED），应始终等于 course.enrolled_count */
    public long seatRows(long courseId) {
        return count("SELECT COUNT(*) FROM enrollment WHERE course_id = ? AND status IN ('ENROLLED', 'COMPLETED')", courseId);
    }

    public void setEnrolledCount(long courseId, int count) {
        jdbc.update("UPDATE course SET enrolled_count = ? WHERE id = ?", count, courseId);
    }

    /** 直接写库造报名记录（PR4 之前用于验证 myStatus 连表） */
    public void insertEnrollment(long courseId, long userId, String status) {
        jdbc.update("""
                INSERT INTO enrollment (course_id, user_id, status, enrolled_at, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW(), NOW())""", courseId, userId, status);
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
