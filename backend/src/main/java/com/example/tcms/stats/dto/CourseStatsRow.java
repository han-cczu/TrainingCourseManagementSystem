package com.example.tcms.stats.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 按课程统计的原始行（StatsMapper.selectCourseStats）。 */
@Data
public class CourseStatsRow {

    private Long id;
    private String title;
    private String instructor;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private int enrolledCount;
    private int completedCount;
}
