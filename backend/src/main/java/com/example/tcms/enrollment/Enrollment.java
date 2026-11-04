package com.example.tcms.enrollment;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;

import lombok.Data;

/**
 * 报名记录（课程 - 用户 多对多关联，技术设计 §5.1）。同一用户在同一课程只有一行，取消后再报名复用该行。
 * <p>
 * 状态转换只能通过下面的方法进行（技术设计 §5.2）：
 * <pre>
 *   (无记录) --报名--> ENROLLED --完成--> COMPLETED（终态）
 *                       |  ^
 *                   取消 |  | 重新报名
 *                       v  |
 *                     CANCELLED
 * </pre>
 * 时间、名额等条件依赖课程和当前时间，由 {@link EnrollmentService} 在锁住课程行后校验。
 */
@Data
@TableName("enrollment")
public class Enrollment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long userId;
    private EnrollmentStatus status;
    private LocalDateTime enrolledAt;
    /** 重新报名时需要清空，updateById 默认跳过 null，因此用 ALWAYS */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;
    /** 等于 userId 表示本人确认，否则为管理员确认 */
    private Long completedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static Enrollment newEnrolled(long courseId, long userId, LocalDateTime now) {
        Enrollment e = new Enrollment();
        e.setCourseId(courseId);
        e.setUserId(userId);
        e.setStatus(EnrollmentStatus.ENROLLED);
        e.setEnrolledAt(now);
        return e;
    }

    /** CANCELLED → ENROLLED：复用原记录，刷新报名时间 */
    public void reEnroll(LocalDateTime now) {
        require(EnrollmentStatus.CANCELLED);
        status = EnrollmentStatus.ENROLLED;
        enrolledAt = now;
        cancelledAt = null;
    }

    /** ENROLLED → CANCELLED */
    public void cancel(LocalDateTime now) {
        require(EnrollmentStatus.ENROLLED);
        status = EnrollmentStatus.CANCELLED;
        cancelledAt = now;
    }

    /** ENROLLED → COMPLETED（终态） */
    public void complete(LocalDateTime now, long byUserId) {
        require(EnrollmentStatus.ENROLLED);
        status = EnrollmentStatus.COMPLETED;
        completedAt = now;
        completedBy = byUserId;
    }

    /** 是否占用名额：已报名和已完成都占（技术设计 §5.1） */
    public boolean occupiesSeat() {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.COMPLETED;
    }

    private void require(EnrollmentStatus expected) {
        if (status != expected) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前报名状态为「" + label(status) + "」，不能执行该操作");
        }
    }

    private static String label(EnrollmentStatus s) {
        return switch (s) {
            case ENROLLED -> "已报名";
            case CANCELLED -> "已取消";
            case COMPLETED -> "已完成";
        };
    }
}
