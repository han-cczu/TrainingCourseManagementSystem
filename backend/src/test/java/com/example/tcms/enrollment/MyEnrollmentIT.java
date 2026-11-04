package com.example.tcms.enrollment;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;

/** 我的报名与个人统计（技术设计 §7.4）。时钟 2026-11-02 09:00。 */
class MyEnrollmentIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);

    private User alice;
    private User bob;
    private long upcoming;
    private long ongoing;
    private long ended;

    @BeforeEach
    void setUp() {
        User admin = data.createAdmin("boss");
        alice = data.createEmployee("alice");
        bob = data.createEmployee("bob");
        upcoming = data.insertCourse("Kubernetes 实战", "李老师", NOW.plusDays(2), NOW.plusDays(2).plusHours(3), 10, admin);
        ongoing = data.insertCourse("Vue 3 实战", "王老师", NOW.minusHours(1), NOW.plusHours(2), 10, admin);
        ended = data.insertCourse("Git 基础", "赵老师", NOW.minusDays(5), NOW.minusDays(5).plusHours(2), 10, admin);

        data.insertEnrollment(upcoming, alice.getId(), "ENROLLED");
        data.insertEnrollment(ongoing, alice.getId(), "CANCELLED");
        data.insertEnrollment(ended, alice.getId(), "COMPLETED");
        data.insertEnrollment(upcoming, bob.getId(), "ENROLLED");
    }

    @Test
    void listsOnlyMyEnrollmentsNewestCourseFirst() throws Exception {
        mvc.perform(get("/api/me/enrollments").header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.records[*].title").value(contains("Kubernetes 实战", "Vue 3 实战", "Git 基础")))
                .andExpect(jsonPath("$.records[*].status").value(contains("ENROLLED", "CANCELLED", "COMPLETED")))
                .andExpect(jsonPath("$.records[*].phase").value(contains("UPCOMING", "ONGOING", "ENDED")))
                .andExpect(jsonPath("$.records[0].courseId").value(upcoming))
                .andExpect(jsonPath("$.records[0].instructor").value("李老师"));

        mvc.perform(get("/api/me/enrollments").header(AUTHORIZATION, data.bearer(bob)))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void filterByStatusAndPaginate() throws Exception {
        mvc.perform(get("/api/me/enrollments").param("status", "COMPLETED").header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].courseId").value(ended));
        mvc.perform(get("/api/me/enrollments").param("page", "2").param("size", "2")
                        .header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.records.length()").value(1));
        mvc.perform(get("/api/me/enrollments").param("status", "WHATEVER").header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myStats() throws Exception {
        mvc.perform(get("/api/me/stats").header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(jsonPath("$.enrolled").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.cancelled").value(1));
        mvc.perform(get("/api/me/stats").header(AUTHORIZATION, data.bearer(data.createEmployee("newbie"))))
                .andExpect(jsonPath("$.enrolled").value(0))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.cancelled").value(0));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/me/enrollments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/stats")).andExpect(status().isUnauthorized());
    }
}
