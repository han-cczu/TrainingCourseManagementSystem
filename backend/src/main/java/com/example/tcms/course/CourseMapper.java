package com.example.tcms.course;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /** 锁住课程行（技术设计 §6）：同一课程的报名、取消、改容量在此串行。必须在事务内调用。 */
    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    Course selectByIdForUpdate(@Param("id") long id);

    /** 调整已报名人数（+1 / -1）。只能在已用 {@link #selectByIdForUpdate} 锁住该课程的事务内调用。 */
    @Update("UPDATE course SET enrolled_count = enrolled_count + #{delta}, updated_at = #{now} WHERE id = #{id}")
    int incrementEnrolledCount(@Param("id") long id, @Param("delta") int delta, @Param("now") LocalDateTime now);

    /**
     * 分页查询课程，并带出 userId 对应用户的报名状态（myStatus）。SQL 见 CourseMapper.xml。
     *
     * @param phase UPCOMING / ONGOING / ENDED 或 null（不筛选）；相对于 now 计算
     */
    IPage<Course> selectCoursePage(IPage<Course> page,
                                   @Param("keyword") String keyword,
                                   @Param("phase") String phase,
                                   @Param("now") LocalDateTime now,
                                   @Param("userId") long userId);

    Course selectCourseDetail(@Param("id") long id, @Param("userId") long userId);
}
