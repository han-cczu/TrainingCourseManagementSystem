package com.example.tcms.enrollment.dto;

import java.time.LocalDateTime;

import com.example.tcms.enrollment.EnrollmentStatus;

import lombok.Data;

/** 课程报名人员列表的一行（连表 sys_user，SQL 见 EnrollmentMapper.xml），仅管理员可见。 */
@Data
public class CourseEnrollmentVO {

    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private String department;
    private EnrollmentStatus status;
    private LocalDateTime enrolledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;
    private Long completedBy;
    /** 完成确认人的姓名 */
    private String completedByName;

    /** 是否本人确认完成（completedBy == userId）；未完成时为 null */
    public Boolean getSelfConfirmed() {
        return completedBy == null ? null : completedBy.equals(userId);
    }
}
