package com.example.tcms.course;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tcms.support.AbstractIntegrationTest;

/** 创建与查看课程、权限、参数校验（技术设计 §5.3、§7.3）。时钟固定在 2026-11-02 09:00。 */
class CourseControllerIT extends AbstractIntegrationTest {

    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUpUsers() {
        adminToken = data.bearer(data.createAdmin("boss"));
        employeeToken = data.bearer(data.createEmployee("emp"));
    }

    static String courseJson(String title, String start, String end, Integer capacity) {
        return """
                {"title": "%s", "description": "介绍", "instructor": "王老师", "location": "3楼会议室",
                 "startTime": "%s", "endTime": "%s", "capacity": %s}
                """.formatted(title, start, end, capacity);
    }

    private ResultActions create(String token, String json) throws Exception {
        return mvc.perform(post("/api/courses").header(AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void adminCreatesCourse() throws Exception {
        create(adminToken, courseJson("Java 入门", "2026-11-05T09:00:00", "2026-11-05T12:00:00", 30))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.enrolledCount").value(0))
                .andExpect(jsonPath("$.remaining").value(30))
                .andExpect(jsonPath("$.phase").value("UPCOMING"))
                .andExpect(jsonPath("$.myStatus").value(nullValue()));
    }

    @Test
    void unlimitedCapacityHasNullRemaining() throws Exception {
        create(adminToken, courseJson("Git 分享", "2026-11-05T09:00:00", "2026-11-05T10:00:00", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(nullValue()))
                .andExpect(jsonPath("$.remaining").value(nullValue()));
    }

    @Test
    void onlyAdminCanCreate() throws Exception {
        String json = courseJson("X", "2026-11-05T09:00:00", "2026-11-05T12:00:00", 10);
        create(employeeToken, json)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createValidatesTimesAndCapacity() throws Exception {
        // 结束早于开始
        create(adminToken, courseJson("X", "2026-11-05T12:00:00", "2026-11-05T09:00:00", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.endTime").exists());
        // 开始时间在过去（当前 2026-11-02 09:00）
        create(adminToken, courseJson("X", "2026-11-01T09:00:00", "2026-11-01T12:00:00", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.startTime").exists());
        // 开始时间恰好等于当前时间也不行
        create(adminToken, courseJson("X", "2026-11-02T09:00:00", "2026-11-02T12:00:00", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.startTime").exists());
        // 容量为 0
        create(adminToken, courseJson("X", "2026-11-05T09:00:00", "2026-11-05T12:00:00", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.capacity").exists());
        // 缺少标题和讲师
        create(adminToken, """
                {"startTime": "2026-11-05T09:00:00", "endTime": "2026-11-05T12:00:00"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.title").exists())
                .andExpect(jsonPath("$.details.fields.instructor").exists());
    }

    @Test
    void employeeCanViewDetail() throws Exception {
        String body = create(adminToken, courseJson("Java 入门", "2026-11-05T09:00:00", "2026-11-05T12:00:00", 30))
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mvc.perform(get("/api/courses/{id}", id.longValue()).header(AUTHORIZATION, employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor").value("王老师"))
                .andExpect(jsonPath("$.startTime").value("2026-11-05T09:00:00"))
                .andExpect(jsonPath("$.phase").value("UPCOMING"));
    }

    @Test
    void detailOfMissingCourseIs404() throws Exception {
        mvc.perform(get("/api/courses/{id}", 999_999).header(AUTHORIZATION, employeeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }
}
