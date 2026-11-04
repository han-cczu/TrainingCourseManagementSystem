package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.example.tcms.support.AbstractIntegrationTest;

/** 精确时间边界与完成终态：避免只测“开始前一小时/开始后一小时”。 */
class EnrollmentBoundaryIT extends AbstractIntegrationTest {

    @Test
    void atExactStartEnrollmentAndCancellationCloseButCompletionOpens() throws Exception {
        var admin = data.createAdmin("boundary_admin");
        var holder = data.createEmployee("boundary_holder");
        var newcomer = data.createEmployee("boundary_newcomer");
        var start = clock.now().plusMinutes(1);
        long course = data.insertCourse("开课边界", "讲师", start, start.plusHours(1), 2, admin);
        mvc.perform(post("/api/courses/{id}/enrollments", course)
                        .header(AUTHORIZATION, data.bearer(holder))).andExpect(status().isCreated());
        clock.setNow(start);
        mvc.perform(post("/api/courses/{id}/enrollments", course)
                        .header(AUTHORIZATION, data.bearer(newcomer)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_STARTED"));
        mvc.perform(delete("/api/courses/{id}/enrollments/me", course)
                        .header(AUTHORIZATION, data.bearer(holder)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_STARTED"));
        mvc.perform(post("/api/courses/{id}/enrollments/me/complete", course)
                        .header(AUTHORIZATION, data.bearer(holder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(data.enrolledCount(course)).isEqualTo(1);
        assertThat(data.seatRows(course)).isEqualTo(1);
    }

    @Test
    void atExactEndCompletionIsStillAllowedAndCannotBeRepeated() throws Exception {
        var admin = data.createAdmin("end_admin");
        var employee = data.createEmployee("end_employee");
        var start = clock.now().plusMinutes(1);
        var end = start.plusHours(1);
        long course = data.insertCourse("结束边界", "讲师", start, end, 1, admin);
        mvc.perform(post("/api/courses/{id}/enrollments", course)
                        .header(AUTHORIZATION, data.bearer(employee))).andExpect(status().isCreated());
        clock.setNow(end);
        mvc.perform(post("/api/courses/{id}/enrollments/me/complete", course)
                        .header(AUTHORIZATION, data.bearer(employee)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedBy").value(employee.getId()));
        mvc.perform(post("/api/courses/{id}/enrollments/me/complete", course)
                        .header(AUTHORIZATION, data.bearer(employee)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(data.enrolledCount(course)).isEqualTo(data.seatRows(course)).isEqualTo(1);
    }
}
