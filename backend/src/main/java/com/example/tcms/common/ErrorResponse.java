package com.example.tcms.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 统一错误响应体：{@code {"code": "COURSE_FULL", "message": "课程名额已满", "details": {...}}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, Object> details) {
}
