package com.example.tcms.stats.dto;

import lombok.Data;

/** 概览统计的原始计数（StatsMapper.selectOverview）。 */
@Data
public class OverviewRow {

    private long courseCount;
    private long upcomingCount;
    private long ongoingCount;
    private long endedCount;
    private long activeEnrollments;
    private long completedCount;
    private long participantCount;
    private long userCount;
}
