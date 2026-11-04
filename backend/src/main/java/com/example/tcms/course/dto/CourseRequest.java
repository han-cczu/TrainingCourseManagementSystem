package com.example.tcms.course.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建 / 编辑课程（两者字段相同）。
 * <p>
 * 结束晚于开始、开始晚于当前时间、已开课不可改时间和容量等规则依赖当前时间和库中数据，在 CourseService 中校验。
 */
public record CourseRequest(
        @NotBlank(message = "课程名称不能为空")
        @Size(max = 100, message = "课程名称最多 100 个字符")
        String title,

        @Size(max = 2000, message = "课程介绍最多 2000 个字符")
        String description,

        @NotBlank(message = "讲师不能为空")
        @Size(max = 32, message = "讲师最多 32 个字符")
        String instructor,

        @Size(max = 100, message = "地点最多 100 个字符")
        String location,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        /* 为空表示不限人数 */
        @Min(value = 1, message = "容量至少为 1")
        @Max(value = 10000, message = "容量最多为 10000")
        Integer capacity) {
}
