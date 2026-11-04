package com.example.tcms.stats.dto;

import java.time.LocalDateTime;

import com.example.tcms.course.CoursePhase;

/**
 * 按课程统计（技术设计 §7.5）。
 *
 * @param enrolledCount  已报名人数 = ENROLLED + COMPLETED
 * @param completionRate 完成数 / 已报名人数；无人报名时为 null
 * @param fillRate       已报名人数 / 容量；不限人数时为 null
 */
public record CourseStatsVO(
        Long courseId,
        String title,
        String instructor,
        LocalDateTime startTime,
        LocalDateTime endTime,
        CoursePhase phase,
        Integer capacity,
        int enrolledCount,
        int completedCount,
        Double completionRate,
        Double fillRate) {

    public static CourseStatsVO from(CourseStatsRow r, LocalDateTime now) {
        return new CourseStatsVO(r.getId(), r.getTitle(), r.getInstructor(), r.getStartTime(), r.getEndTime(),
                CoursePhase.of(r.getStartTime(), r.getEndTime(), now),
                r.getCapacity(), r.getEnrolledCount(), r.getCompletedCount(),
                Rates.ratio(r.getCompletedCount(), r.getEnrolledCount()),
                r.getCapacity() == null ? null : Rates.ratio(r.getEnrolledCount(), r.getCapacity()));
    }
}
