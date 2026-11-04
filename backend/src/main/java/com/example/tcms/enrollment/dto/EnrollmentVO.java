package com.example.tcms.enrollment.dto;

import java.time.LocalDateTime;

import com.example.tcms.enrollment.Enrollment;
import com.example.tcms.enrollment.EnrollmentStatus;

/** 报名/取消/完成操作的返回值。 */
public record EnrollmentVO(
        Long id,
        Long courseId,
        Long userId,
        EnrollmentStatus status,
        LocalDateTime enrolledAt,
        LocalDateTime cancelledAt,
        LocalDateTime completedAt,
        Long completedBy) {

    public static EnrollmentVO from(Enrollment e) {
        return new EnrollmentVO(e.getId(), e.getCourseId(), e.getUserId(), e.getStatus(),
                e.getEnrolledAt(), e.getCancelledAt(), e.getCompletedAt(), e.getCompletedBy());
    }
}
