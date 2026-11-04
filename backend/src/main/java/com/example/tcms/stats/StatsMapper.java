package com.example.tcms.stats;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.tcms.stats.dto.CourseStatsRow;
import com.example.tcms.stats.dto.OverviewRow;

/** 统计聚合查询，SQL 见 resources/mapper/StatsMapper.xml。now 由调用方传入，便于测试控制时间。 */
@Mapper
public interface StatsMapper {

    OverviewRow selectOverview(@Param("now") LocalDateTime now);

    /** @param sort CourseStatsSort 的枚举名，在 XML 中映射为固定的 ORDER BY */
    List<CourseStatsRow> selectCourseStats(@Param("sort") String sort, @Param("limit") int limit);
}
