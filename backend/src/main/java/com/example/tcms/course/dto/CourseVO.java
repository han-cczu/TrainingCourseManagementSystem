package com.example.tcms.course.dto;

import java.time.LocalDateTime;

import com.example.tcms.course.Course;
import com.example.tcms.course.CoursePhase;
import com.example.tcms.enrollment.EnrollmentStatus;

/**
 * 课程视图（技术设计 §7.3）。
 *
 * @param remaining 剩余名额；不限人数时为 null
 * @param phase     按当前时间计算的阶段
 * @param myStatus  当前用户的报名状态；未报名为 null
 */
public record CourseVO(
        Long id,
        String title,
        String description,
        String instructor,
        String location,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer capacity,
        int enrolledCount,
        Integer remaining,
        CoursePhase phase,
        EnrollmentStatus myStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CourseVO from(Course c, LocalDateTime now) {
        int enrolled = c.getEnrolledCount() == null ? 0 : c.getEnrolledCount();
        Integer remaining = c.getCapacity() == null ? null : Math.max(c.getCapacity() - enrolled, 0);
        return new CourseVO(c.getId(), c.getTitle(), c.getDescription(), c.getInstructor(), c.getLocation(),
                c.getStartTime(), c.getEndTime(), c.getCapacity(), enrolled, remaining,
                CoursePhase.of(c.getStartTime(), c.getEndTime(), now), c.getMyStatus(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
