package com.example.tcms.stats.dto;

/**
 * 全局概览（技术设计 §7.5）。
 *
 * @param activeEnrollments 有效报名人次 = ENROLLED + COMPLETED
 * @param completionRate    完成人次 / 有效报名人次，0~1；无有效报名时为 null
 * @param participantCount  至少有一条有效报名的不同用户数
 */
public record OverviewVO(
        long courseCount,
        long upcomingCount,
        long ongoingCount,
        long endedCount,
        long activeEnrollments,
        long completedCount,
        Double completionRate,
        long participantCount,
        long userCount) {

    public static OverviewVO from(OverviewRow r) {
        return new OverviewVO(r.getCourseCount(), r.getUpcomingCount(), r.getOngoingCount(), r.getEndedCount(),
                r.getActiveEnrollments(), r.getCompletedCount(),
                Rates.ratio(r.getCompletedCount(), r.getActiveEnrollments()),
                r.getParticipantCount(), r.getUserCount());
    }
}
