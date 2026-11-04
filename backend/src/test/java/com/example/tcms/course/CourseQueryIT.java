package com.example.tcms.course;

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

/**
 * 列表查询：关键字、阶段筛选、排序、分页、myStatus（技术设计 §7.3）。
 * 时钟固定在 2026-11-02 09:00。
 */
class CourseQueryIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);

    private User employee;
    private String employeeToken;
    private long upcomingSoon;
    private long upcomingLater;
    private long ongoing;
    private long endedRecent;

    @BeforeEach
    void setUp() {
        User admin = data.createAdmin("boss");
        employee = data.createEmployee("emp");
        employeeToken = data.bearer(employee);

        upcomingLater = data.insertCourse("Kubernetes 实战", "李老师", NOW.plusDays(7), NOW.plusDays(7).plusHours(3), 20, admin);
        upcomingSoon = data.insertCourse("Java 入门", "王老师", NOW.plusDays(1), NOW.plusDays(1).plusHours(3), 30, admin);
        ongoing = data.insertCourse("Vue 3 实战", "王老师", NOW.minusHours(1), NOW.plusHours(2), null, admin);
        data.insertCourse("Git 基础", "赵老师", NOW.minusDays(10), NOW.minusDays(10).plusHours(2), 50, admin);
        endedRecent = data.insertCourse("MySQL 优化", "赵老师", NOW.minusDays(2), NOW.minusDays(2).plusHours(2), 50, admin);
    }

    @Test
    void defaultOrderPutsUpcomingFirstAscendingThenOthersDescending() throws Exception {
        mvc.perform(get("/api/courses").header(AUTHORIZATION, employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.records[*].title").value(contains(
                        "Java 入门", "Kubernetes 实战", "Vue 3 实战", "MySQL 优化", "Git 基础")))
                .andExpect(jsonPath("$.records[*].phase").value(contains(
                        "UPCOMING", "UPCOMING", "ONGOING", "ENDED", "ENDED")));
    }

    @Test
    void filterByPhase() throws Exception {
        mvc.perform(get("/api/courses").param("phase", "UPCOMING").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.records[*].id").value(contains((int) upcomingSoon, (int) upcomingLater)));
        mvc.perform(get("/api/courses").param("phase", "ONGOING").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.records[*].id").value(contains((int) ongoing)));
        mvc.perform(get("/api/courses").param("phase", "ENDED").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.records[0].id").value(endedRecent));
        mvc.perform(get("/api/courses").param("phase", "SOMETIME").header(AUTHORIZATION, employeeToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void phaseFollowsTheClock() throws Exception {
        clock.setNow(NOW.plusDays(1).plusMinutes(1)); // "Java 入门" 刚开课
        employeeToken = data.bearer(employee);          // 时钟拨过 8 小时，旧 token 已过期
        mvc.perform(get("/api/courses/{id}", upcomingSoon).header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.phase").value("ONGOING"));
    }

    @Test
    void keywordMatchesTitleOrInstructor() throws Exception {
        mvc.perform(get("/api/courses").param("keyword", "实战").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/courses").param("keyword", "赵老师").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/courses").param("keyword", "实战").param("phase", "ONGOING")
                        .header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.records[*].title").value(contains("Vue 3 实战")));
    }

    @Test
    void pagination() throws Exception {
        mvc.perform(get("/api/courses").param("page", "2").param("size", "2").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.records[*].title").value(contains("Vue 3 实战", "MySQL 优化")));
        mvc.perform(get("/api/courses").param("size", "101").header(AUTHORIZATION, employeeToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myStatusReflectsOnlyCurrentUsersEnrollment() throws Exception {
        data.insertEnrollment(upcomingSoon, employee.getId(), "ENROLLED");
        User other = data.createEmployee("other");
        data.insertEnrollment(upcomingLater, other.getId(), "ENROLLED");

        mvc.perform(get("/api/courses").param("phase", "UPCOMING").header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.records[0].myStatus").value("ENROLLED"))
                .andExpect(jsonPath("$.records[1].myStatus").doesNotExist());
        mvc.perform(get("/api/courses/{id}", upcomingSoon).header(AUTHORIZATION, employeeToken))
                .andExpect(jsonPath("$.myStatus").value("ENROLLED"));
    }
}
