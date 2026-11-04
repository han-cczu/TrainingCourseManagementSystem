package com.example.tcms.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** 阶段边界（技术设计 §5.3）：开始时间点已不可报名（ONGOING），结束时间点即 ENDED。 */
class CoursePhaseTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 10, 12, 0);

    @Test
    void boundaries() {
        assertThat(CoursePhase.of(START, END, START.minusSeconds(1))).isEqualTo(CoursePhase.UPCOMING);
        assertThat(CoursePhase.of(START, END, START)).isEqualTo(CoursePhase.ONGOING);
        assertThat(CoursePhase.of(START, END, END.minusSeconds(1))).isEqualTo(CoursePhase.ONGOING);
        assertThat(CoursePhase.of(START, END, END)).isEqualTo(CoursePhase.ENDED);
        assertThat(CoursePhase.of(START, END, END.plusDays(1))).isEqualTo(CoursePhase.ENDED);
    }
}
