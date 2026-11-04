package com.example.tcms.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.tcms.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** 请求类型与编辑空值的遗漏场景。 */
class RequestBoundaryIT extends AbstractIntegrationTest {

    private static String course(String capacity, String optionalFields) {
        return """
                {"title":"边界课程","instructor":"讲师", "startTime":"2026-11-05T09:00:00",
                 "endTime":"2026-11-05T11:00:00","capacity":%s%s}
                """.formatted(capacity, optionalFields);
    }

    @Test
    void fractionalCapacityMustNotBeSilentlyTruncated() throws Exception {
        String token = data.bearer(data.createAdmin("capacity_admin"));
        mvc.perform(post("/api/courses").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(course("1.9", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(data.count("SELECT COUNT(*) FROM course")).isZero();
    }

    @Test
    void unsupportedContentTypeIsClientError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"username\":\"anyone\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void clearingDescriptionAndLocationIsPersisted() throws Exception {
        String token = data.bearer(data.createAdmin("clear_admin"));
        String body = mvc.perform(post("/api/courses").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(course("10", ",\"description\":\"介绍\",\"location\":\"会议室\"")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(put("/api/courses/{id}", id).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(course("10", ",\"description\":\"   \",\"location\":\"\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.location").value(nullValue()));
        assertThat(data.count("SELECT COUNT(*) FROM course WHERE id = ? AND description IS NULL AND location IS NULL", id))
                .isEqualTo(1);
    }
}
