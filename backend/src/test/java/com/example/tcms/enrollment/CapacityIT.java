package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;

/** 扩展任务：课程容量限制（技术设计 §5.2、§6）。 */
class CapacityIT extends AbstractIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);

    private User admin;

    @BeforeEach
    void setUp() {
        admin = data.createAdmin("boss");
    }

    private ResultActions enroll(long courseId, User user) throws Exception {
        return mvc.perform(post("/api/courses/{id}/enrollments", courseId).header(AUTHORIZATION, data.bearer(user)));
    }

    @Test
    void fullCourseRejectsUntilSomeoneCancels() throws Exception {
        long courseId = data.insertCourse("小班课", "王老师", START, START.plusHours(2), 2, admin);
        User a = data.createEmployee("a");
        User b = data.createEmployee("b");
        User c = data.createEmployee("c");

        enroll(courseId, a).andExpect(status().isCreated());
        enroll(courseId, b).andExpect(status().isCreated());
        enroll(courseId, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_FULL"));
        mvc.perform(get("/api/courses/{id}", courseId).header(AUTHORIZATION, data.bearer(c)))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.myStatus").doesNotExist());

        mvc.perform(delete("/api/courses/{id}/enrollments/me", courseId).header(AUTHORIZATION, data.bearer(b)))
                .andExpect(status().isNoContent());
        enroll(courseId, c).andExpect(status().isCreated());

        assertThat(data.enrolledCount(courseId)).isEqualTo(2);
        assertThat(data.seatRows(courseId)).isEqualTo(2);
        // b 取消后想再报名，此时已满
        enroll(courseId, b).andExpect(jsonPath("$.code").value("COURSE_FULL"));
    }

    @Test
    void completedEnrollmentStillOccupiesSeat() throws Exception {
        long courseId = data.insertCourse("一对一", "王老师", START, START.plusHours(2), 1, admin);
        User a = data.createEmployee("a");
        enroll(courseId, a).andExpect(status().isCreated());

        clock.setNow(START);
        mvc.perform(post("/api/courses/{id}/enrollments/me/complete", courseId).header(AUTHORIZATION, data.bearer(a)))
                .andExpect(status().isOk());
        assertThat(data.enrolledCount(courseId)).isEqualTo(1);
        mvc.perform(get("/api/courses/{id}", courseId).header(AUTHORIZATION, data.bearer(a)))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.myStatus").value("COMPLETED"));
    }

    @Test
    void unlimitedCourseAcceptsEveryone() throws Exception {
        long courseId = data.insertCourse("全员大会", "CEO", START, START.plusHours(1), null, admin);
        for (int i = 0; i < 5; i++) {
            enroll(courseId, data.createEmployee("u" + i)).andExpect(status().isCreated());
        }
        assertThat(data.enrolledCount(courseId)).isEqualTo(5);
    }

    @Test
    void capacityCannotBeEditedBelowRealEnrollments() throws Exception {
        long courseId = data.insertCourse("小班课", "王老师", START, START.plusHours(2), 3, admin);
        enroll(courseId, data.createEmployee("a")).andExpect(status().isCreated());
        enroll(courseId, data.createEmployee("b")).andExpect(status().isCreated());

        mvc.perform(put("/api/courses/{id}", courseId).header(AUTHORIZATION, data.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "小班课", "instructor": "王老师",
                                 "startTime": "2026-11-03T09:00:00", "endTime": "2026-11-03T11:00:00", "capacity": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_ENROLLED"));
    }
}
