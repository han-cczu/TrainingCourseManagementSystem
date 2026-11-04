package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;
import com.jayway.jsonpath.JsonPath;

/** 课程报名人员列表（管理员）与"确认方式"（技术设计 §7.4）。 */
class CourseEnrollmentListIT extends AbstractIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Autowired
    private EnrollmentService enrollmentService;

    private User admin;
    private User alice;
    private long courseId;

    @BeforeEach
    void setUp() {
        admin = data.createAdmin("boss");
        alice = data.createEmployee("alice");
        User bob = data.createEmployee("bob");
        User carol = data.createEmployee("carol");
        courseId = data.insertCourse("Java 入门", "王老师", START, START.plusHours(3), 10, admin);

        enrollmentService.enroll(courseId, data.currentUser(alice));
        long bobEnrollment = enrollmentService.enroll(courseId, data.currentUser(bob)).id();
        enrollmentService.enroll(courseId, data.currentUser(carol));
        enrollmentService.cancel(courseId, data.currentUser(carol));

        clock.setNow(START.plusMinutes(5));
        enrollmentService.completeSelf(courseId, data.currentUser(alice));
        enrollmentService.completeByAdmin(bobEnrollment, data.currentUser(admin));
    }

    /** 从列表中取出某个用户那一行的字段 */
    private static <T> List<T> field(String body, String username, String field) {
        return JsonPath.read(body, "$.records[?(@.username == '%s')].%s".formatted(username, field));
    }

    @Test
    void adminSeesEnrolleesWithConfirmationSource() throws Exception {
        String body = mvc.perform(get("/api/courses/{id}/enrollments", courseId).header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.records[*].username").value(containsInAnyOrder("alice", "bob", "carol")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // alice 本人确认，bob 由管理员确认，carol 已取消
        assertThat(field(body, "alice", "selfConfirmed")).containsExactly(true);
        assertThat(field(body, "bob", "selfConfirmed")).containsExactly(false);
        assertThat(field(body, "bob", "completedByName")).containsExactly("测试-boss");
        assertThat(field(body, "carol", "status")).containsExactly("CANCELLED");
        assertThat(field(body, "alice", "realName")).containsExactly("测试-alice");
    }

    @Test
    void filterByStatus() throws Exception {
        mvc.perform(get("/api/courses/{id}/enrollments", courseId).param("status", "COMPLETED")
                        .header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/courses/{id}/enrollments", courseId).param("status", "CANCELLED")
                        .header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(jsonPath("$.records[0].username").value("carol"));
    }

    @Test
    void employeeForbiddenAndMissingCourse404() throws Exception {
        mvc.perform(get("/api/courses/{id}/enrollments", courseId).header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/{id}/enrollments", 999_999).header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }
}
