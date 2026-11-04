package com.example.tcms.stats;

/** 课程统计的排序方式。在 StatsMapper.xml 中映射为固定的 ORDER BY，不接受外部传入的列名。 */
public enum CourseStatsSort {
    /** 按已报名人数降序 */
    ENROLLED,
    /** 按完成率降序（无人报名的课程完成率为 null，排在最后） */
    COMPLETION_RATE
}
