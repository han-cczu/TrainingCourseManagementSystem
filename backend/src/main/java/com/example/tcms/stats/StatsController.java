package com.example.tcms.stats;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.tcms.stats.dto.CourseStatsVO;
import com.example.tcms.stats.dto.OverviewVO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 基础统计（技术设计 §7.5），仅管理员。个人统计见 GET /api/me/stats。 */
@RestController
@RequestMapping("/api/stats")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public OverviewVO overview() {
        return statsService.overview();
    }

    @GetMapping("/courses")
    public List<CourseStatsVO> courses(
            @RequestParam(defaultValue = "ENROLLED") CourseStatsSort sort,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return statsService.courseStats(sort, limit);
    }
}
