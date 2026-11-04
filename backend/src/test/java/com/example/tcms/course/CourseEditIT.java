package com.example.tcms.course;

import static com.example.tcms.course.CourseControllerIT.courseJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
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

/** 编辑规则（技术设计 §5.3）：开课前可改全部；开课后不能改时间和容量；容量不能小于已报名人数。 */
class CourseEditIT extends AbstractIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final String START_S = "2026-11-05T09:00:00";
    private static final String END_S = "2026-11-05T12:00:00";

    private User admin;
    private String adminToken;
    private long courseId;

    @BeforeEach
    void setUp() {
        admin = data.createAdmin("boss");
        adminToken = data.bearer(admin);
        courseId = data.insertCourse("Java 入门", "王老师", START, END, 10, admin);
    }

    private ResultActions update(String json) throws Exception {
        return mvc.perform(put("/api/courses/{id}", courseId).header(AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void beforeStartAllFieldsCanChange() throws Exception {
        update(courseJson("Java 进阶", "2026-11-06T14:00:00", "2026-11-06T17:00:00", 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java 进阶"))
                .andExpect(jsonPath("$.startTime").value("2026-11-06T14:00:00"))
                .andExpect(jsonPath("$.capacity").value(20));
    }

    @Test
    void capacityCanBeClearedToUnlimited() throws Exception {
        // updateById 默认忽略 null，capacity 需要 FieldStrategy.ALWAYS 才能被更新为 NULL
        update(courseJson("Java 入门", START_S, END_S, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(nullValue()))
                .andExpect(jsonPath("$.remaining").value(nullValue()));
        assertThat(data.count("SELECT COUNT(*) FROM course WHERE id = ? AND capacity IS NULL", courseId)).isEqualTo(1);
    }

    @Test
    void beforeStartCannotMoveStartIntoPast() throws Exception {
        update(courseJson("Java 入门", "2026-11-01T09:00:00", "2026-11-01T12:00:00", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.startTime").exists());
    }

    /** 时钟拨过 8 小时后原 token 已过期，需按新时间重新签发 */
    private void moveClockTo(LocalDateTime now) {
        clock.setNow(now);
        adminToken = data.bearer(admin);
    }

    @Test
    void afterStartScheduleIsLockedButOtherFieldsEditable() throws Exception {
        moveClockTo(START.plusMinutes(30));

        update(courseJson("Java 入门", "2026-11-05T10:00:00", END_S, 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_STARTED"));
        update(courseJson("Java 入门", START_S, "2026-11-05T13:00:00", 10))
                .andExpect(status().isConflict());
        update(courseJson("Java 入门", START_S, END_S, 15))
                .andExpect(status().isConflict());
        update(courseJson("Java 入门", START_S, END_S, null))
                .andExpect(status().isConflict());

        // 时间和容量与原值一致，只改标题：允许
        update(courseJson("Java 入门（第二期）", START_S, END_S, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java 入门（第二期）"))
                .andExpect(jsonPath("$.phase").value("ONGOING"));
    }

    @Test
    void sameInstantWithFractionalSecondsIsNotTreatedAsChange() throws Exception {
        moveClockTo(START.plusMinutes(30));
        update(courseJson("改个标题", "2026-11-05T09:00:00.000", "2026-11-05T12:00:00.000", 10))
                .andExpect(status().isOk());
    }

    @Test
    void capacityCannotBeBelowEnrolledCount() throws Exception {
        data.setEnrolledCount(courseId, 3);
        update(courseJson("Java 入门", START_S, END_S, 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_ENROLLED"))
                .andExpect(jsonPath("$.details.enrolledCount").value(3));
        update(courseJson("Java 入门", START_S, END_S, 3)).andExpect(status().isOk());
    }

    @Test
    void employeeCannotEditAndMissingCourseIs404() throws Exception {
        String employeeToken = data.bearer(data.createEmployee("emp"));
        mvc.perform(put("/api/courses/{id}", courseId).header(AUTHORIZATION, employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(courseJson("X", START_S, END_S, 10)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/courses/{id}", 999_999).header(AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(courseJson("X", START_S, END_S, 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }
}
