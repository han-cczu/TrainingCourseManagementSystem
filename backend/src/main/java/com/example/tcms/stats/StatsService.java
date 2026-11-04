package com.example.tcms.stats;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.tcms.stats.dto.CourseStatsVO;
import com.example.tcms.stats.dto.OverviewVO;

import lombok.RequiredArgsConstructor;

/** 基础统计：实时聚合，数据量小，不做预计算（技术设计 §7.5）。 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;
    private final Clock clock;

    public OverviewVO overview() {
        return OverviewVO.from(statsMapper.selectOverview(now()));
    }

    public List<CourseStatsVO> courseStats(CourseStatsSort sort, int limit) {
        LocalDateTime now = now();
        return statsMapper.selectCourseStats(sort.name(), limit).stream()
                .map(row -> CourseStatsVO.from(row, now))
                .toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
