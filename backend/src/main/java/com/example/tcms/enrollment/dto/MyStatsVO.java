package com.example.tcms.enrollment.dto;

/** 我的报名统计：各状态的课程数。 */
public record MyStatsVO(long enrolled, long completed, long cancelled) {
}
