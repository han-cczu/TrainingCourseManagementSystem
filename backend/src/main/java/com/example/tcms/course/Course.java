package com.example.tcms.course;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.tcms.enrollment.EnrollmentStatus;

import lombok.Data;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;

    /* 可清空的字段：updateById 默认跳过 null，需 ALWAYS 才能把值更新为 NULL */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String instructor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String location;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 容量，null 表示不限人数 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer capacity;
    /** 冗余计数 = ENROLLED + COMPLETED 人数，只能在锁住课程行的事务里修改（技术设计 §6） */
    private Integer enrolledCount;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 查询时连表带出的"当前用户在该课程的报名状态"，不是 course 表的列 */
    @TableField(exist = false)
    private EnrollmentStatus myStatus;
}
