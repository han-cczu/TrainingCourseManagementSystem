package com.example.tcms.enrollment.dto;

import java.time.LocalDateTime;

import com.example.tcms.course.CoursePhase;
import com.example.tcms.enrollment.EnrollmentStatus;

import lombok.Data;

/** 我的报名列表的一行（连表 course，SQL 见 EnrollmentMapper.xml）。 */
@Data
public class MyEnrollmentVO {

    private Long id;
    private EnrollmentStatus status;
    private LocalDateTime enrolledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;
    private Long completedBy;

    private Long courseId;
    private String title;
    private String instructor;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 课程阶段，查询后按当前时间填充 */
    private CoursePhase phase;
}
