package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;
import com.jayway.jsonpath.JsonPath;

/**
 * 报名 → 取消 → 再报名 → 完成的完整流程与时间规则（技术设计 §5.2、§5.3、§7.4）。
 * 时钟默认 2026-11-02 09:00，课程 2026-11-03 09:00 开课。
 */
class EnrollmentFlowIT extends AbstractIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);

    private User admin;
    private User alice;
    private long courseId;

    @BeforeEach
    void setUp() {
        admin = data.createAdmin("boss");
        alice = data.createEmployee("alice");
        courseId = data.insertCourse("Java 入门", "王老师", START, START.plusHours(3), 10, admin);
    }

    private ResultActions enroll(User user) throws Exception {
        return mvc.perform(post("/api/courses/{id}/enrollments", courseId).header(AUTHORIZATION, data.bearer(user)));
    }

    private ResultActions cancel(User user) throws Exception {
        return mvc.perform(delete("/api/courses/{id}/enrollments/me", courseId).header(AUTHORIZATION, data.bearer(user)));
    }

    private ResultActions completeSelf(User user) throws Exception {
        return mvc.perform(post("/api/courses/{id}/enrollments/me/complete", courseId)
                .header(AUTHORIZATION, data.bearer(user)));
    }

    private ResultActions myStatus(User user) throws Exception {
        return mvc.perform(get("/api/courses/{id}", courseId).header(AUTHORIZATION, data.bearer(user)));
    }

    @Test
    void enrollCancelAndReEnrollReuseTheSameRow() throws Exception {
        String body = enroll(alice)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn().getResponse().getContentAsString();
        Number firstId = JsonPath.read(body, "$.id");
        assertThat(data.enrolledCount(courseId)).isEqualTo(1);
        myStatus(alice).andExpect(jsonPath("$.myStatus").value("ENROLLED")).andExpect(jsonPath("$.remaining").value(9));

        cancel(alice).andExpect(status().isNoContent());
        assertThat(data.enrolledCount(courseId)).isZero();
        myStatus(alice).andExpect(jsonPath("$.myStatus").value("CANCELLED"));

        enroll(alice)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId.longValue()))
                .andExpect(jsonPath("$.cancelledAt").doesNotExist());
        assertThat(data.enrolledCount(courseId)).isEqualTo(1);
        assertThat(data.count("SELECT COUNT(*) FROM enrollment WHERE course_id = ?", courseId)).isEqualTo(1);
    }

    @Test
    void duplicateEnrollIsRejected() throws Exception {
        enroll(alice).andExpect(status().isCreated());
        enroll(alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
        assertThat(data.enrolledCount(courseId)).isEqualTo(1);
    }

    @Test
    void invalidTransitionsAreRejected() throws Exception {
        cancel(alice)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));

        enroll(alice).andExpect(status().isCreated());
        cancel(alice).andExpect(status().isNoContent());
        cancel(alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(data.enrolledCount(courseId)).isZero();

        mvc.perform(post("/api/courses/{id}/enrollments", 999_999).header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void timeRules() throws Exception {
        User bob = data.createEmployee("bob");
        enroll(alice).andExpect(status().isCreated());
        enroll(bob).andExpect(status().isCreated());

        // 开课前不能标记完成
        completeSelf(alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_STARTED"));

        // 到达开课时间：不能报名、不能取消，可以标记完成（bearer() 按新时间重新签发 token）
        clock.setNow(START);
        User carol = data.createEmployee("carol");
        enroll(carol)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_STARTED"));
        cancel(bob)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_STARTED"));

        completeSelf(alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedBy").value(alice.getId()));
        completeSelf(alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // 完成不释放名额
        assertThat(data.enrolledCount(courseId)).isEqualTo(2);
        assertThat(data.seatRows(courseId)).isEqualTo(2);
    }

    @Test
    void adminCompletesOnBehalfOfEmployee() throws Exception {
        String body = enroll(alice).andReturn().getResponse().getContentAsString();
        long enrollmentId = ((Number) JsonPath.read(body, "$.id")).longValue();

        // 开课前管理员也不能标记完成
        mvc.perform(post("/api/enrollments/{id}/complete", enrollmentId).header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_STARTED"));

        clock.setNow(START.plusMinutes(10));
        // 员工不能调用管理员接口，即使是自己的报名
        mvc.perform(post("/api/enrollments/{id}/complete", enrollmentId).header(AUTHORIZATION, data.bearer(alice)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/enrollments/{id}/complete", enrollmentId).header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedBy").value(admin.getId()));

        mvc.perform(post("/api/enrollments/{id}/complete", 999_999).header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    void cancelledEnrollmentCannotBeCompletedByAdmin() throws Exception {
        String body = enroll(alice).andReturn().getResponse().getContentAsString();
        long enrollmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
        cancel(alice).andExpect(status().isNoContent());

        clock.setNow(START.plusMinutes(10));
        mvc.perform(post("/api/enrollments/{id}/complete", enrollmentId).header(AUTHORIZATION, data.bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }
}
