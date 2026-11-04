package com.example.tcms.stats;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;

/**
 * 基础统计（技术设计 §7.5）。时钟 2026-11-02 09:00。
 *
 * <pre>
 * 课程（容量）        报名情况                                   已报名 完成 完成率 占用率
 * Kubernetes(10)  未开始  u1 u2 u3 u5 已报名，u4 已取消              4     0    0     0.4
 * Vue 3(不限)     进行中  u1 已完成，u4 已报名                         2     1    0.5   null
 * Git(4)          已结束  u2 u3 u5 已完成，u4 已取消                   3     3    1.0   0.75
 * 空课(5)          未开始  无                                         0     0    null  0
 * </pre>
 * 按报名人数排序：Kubernetes、Git、Vue、空课；按完成率排序：Git、Vue、Kubernetes、空课（两种顺序不同）。
 */
class StatsIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);

    private User admin;

    private void seed() {
        admin = data.createAdmin("boss");
        User u1 = data.createEmployee("u1");
        User u2 = data.createEmployee("u2");
        User u3 = data.createEmployee("u3");
        User u4 = data.createEmployee("u4");
        User u5 = data.createEmployee("u5");

        long kube = data.insertCourse("Kubernetes 实战", "李老师", NOW.plusDays(2), NOW.plusDays(2).plusHours(3), 10, admin);
        long vue = data.insertCourse("Vue 3 实战", "王老师", NOW.minusHours(1), NOW.plusHours(2), null, admin);
        long git = data.insertCourse("Git 基础", "赵老师", NOW.minusDays(5), NOW.minusDays(5).plusHours(2), 4, admin);
        data.insertCourse("空课", "钱老师", NOW.plusDays(3), NOW.plusDays(3).plusHours(1), 5, admin);

        for (User u : new User[] {u1, u2, u3, u5}) {
            data.insertEnrollment(kube, u.getId(), "ENROLLED");
        }
        data.insertEnrollment(kube, u4.getId(), "CANCELLED");
        data.setEnrolledCount(kube, 4);

        data.insertEnrollment(vue, u1.getId(), "COMPLETED");
        data.insertEnrollment(vue, u4.getId(), "ENROLLED");
        data.setEnrolledCount(vue, 2);

        for (User u : new User[] {u2, u3, u5}) {
            data.insertEnrollment(git, u.getId(), "COMPLETED");
        }
        data.insertEnrollment(git, u4.getId(), "CANCELLED");
        data.setEnrolledCount(git, 3);
    }

    @Test
    void overview() throws Exception {
        seed();
        mvc.perform(get("/api/stats/overview").header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCount").value(4))
                .andExpect(jsonPath("$.upcomingCount").value(2))
                .andExpect(jsonPath("$.ongoingCount").value(1))
                .andExpect(jsonPath("$.endedCount").value(1))
                .andExpect(jsonPath("$.activeEnrollments").value(9))     // 4 + 2 + 3，不含已取消
                .andExpect(jsonPath("$.completedCount").value(4))       // Vue 1 + Git 3
                .andExpect(jsonPath("$.completionRate").value(0.4444))  // 4 / 9，保留 4 位
                .andExpect(jsonPath("$.participantCount").value(5))     // u1~u5 都至少有一条有效报名
                .andExpect(jsonPath("$.userCount").value(6));
    }

    @Test
    void overviewOnEmptyDatabaseHasNullRate() throws Exception {
        User boss = data.createAdmin("boss");
        mvc.perform(get("/api/stats/overview").header(AUTHORIZATION, data.bearer(boss)))
                .andExpect(jsonPath("$.courseCount").value(0))
                .andExpect(jsonPath("$.activeEnrollments").value(0))
                .andExpect(jsonPath("$.completionRate").value(nullValue()))
                .andExpect(jsonPath("$.userCount").value(1));
    }

    @Test
    void courseStatsSortedByEnrolled() throws Exception {
        seed();
        mvc.perform(get("/api/stats/courses").header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("Kubernetes 实战", "Git 基础", "Vue 3 实战", "空课")))
                .andExpect(jsonPath("$[*].enrolledCount").value(contains(4, 3, 2, 0)))
                .andExpect(jsonPath("$[*].completedCount").value(contains(0, 3, 1, 0)))
                .andExpect(jsonPath("$[*].phase").value(contains("UPCOMING", "ENDED", "ONGOING", "UPCOMING")))
                // 完成率：0、1.0、0.5、无人报名为 null
                .andExpect(jsonPath("$[0].completionRate").value(0.0))
                .andExpect(jsonPath("$[1].completionRate").value(1.0))
                .andExpect(jsonPath("$[2].completionRate").value(0.5))
                .andExpect(jsonPath("$[3].completionRate").value(nullValue()))
                // 占用率：0.4、0.75、不限人数为 null、0
                .andExpect(jsonPath("$[0].fillRate").value(0.4))
                .andExpect(jsonPath("$[1].fillRate").value(0.75))
                .andExpect(jsonPath("$[2].fillRate").value(nullValue()))
                .andExpect(jsonPath("$[3].fillRate").value(0.0));
    }

    @Test
    void courseStatsSortedByCompletionRateWithLimit() throws Exception {
        seed();
        mvc.perform(get("/api/stats/courses").param("sort", "COMPLETION_RATE").header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(jsonPath("$[*].title").value(contains("Git 基础", "Vue 3 实战", "Kubernetes 实战", "空课")));
        mvc.perform(get("/api/stats/courses").param("sort", "COMPLETION_RATE").param("limit", "2")
                        .header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].title").value(contains("Git 基础", "Vue 3 实战")));
    }

    @Test
    void validationAndPermissions() throws Exception {
        seed();
        String adminToken = data.bearer(admin);
        mvc.perform(get("/api/stats/courses").param("limit", "51").header(AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/stats/courses").param("limit", "0").header(AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());
        // 排序只接受枚举值，任意列名或 SQL 片段都会被拒绝
        mvc.perform(get("/api/stats/courses").param("sort", "title; DROP TABLE course").header(AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());

        String employeeToken = data.bearer(data.createEmployee("emp"));
        mvc.perform(get("/api/stats/overview").header(AUTHORIZATION, employeeToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/stats/courses").header(AUTHORIZATION, employeeToken))
                .andExpect(status().isForbidden());
    }
}
