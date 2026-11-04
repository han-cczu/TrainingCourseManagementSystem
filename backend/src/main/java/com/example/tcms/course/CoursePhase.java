package com.example.tcms.course;

import java.time.LocalDateTime;

/**
 * 课程阶段，不落库，按当前时间实时计算（技术设计 §5.3）。
 * <p>
 * 边界：到达开始时间即为 ONGOING（与"now >= start_time 不可报名/取消"一致），到达结束时间即为 ENDED。
 */
public enum CoursePhase {
    /** 未开始，可报名 */
    UPCOMING,
    /** 进行中 */
    ONGOING,
    /** 已结束 */
    ENDED;

    public static CoursePhase of(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (now.isBefore(startTime)) {
            return UPCOMING;
        }
        return now.isBefore(endTime) ? ONGOING : ENDED;
    }
}
