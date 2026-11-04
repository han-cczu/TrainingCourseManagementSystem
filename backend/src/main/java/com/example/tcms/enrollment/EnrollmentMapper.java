package com.example.tcms.enrollment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tcms.enrollment.dto.CourseEnrollmentVO;
import com.example.tcms.enrollment.dto.MyEnrollmentVO;
import com.example.tcms.enrollment.dto.StatusCount;

/** 连表查询见 resources/mapper/EnrollmentMapper.xml。 */
@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {

    /** 课程报名人员（连表 sys_user），status 为 null 时不筛选 */
    IPage<CourseEnrollmentVO> selectCourseEnrollments(IPage<CourseEnrollmentVO> page,
                                                      @Param("courseId") long courseId,
                                                      @Param("status") EnrollmentStatus status);

    /** 我的报名（连表 course），status 为 null 时不筛选 */
    IPage<MyEnrollmentVO> selectMyEnrollments(IPage<MyEnrollmentVO> page,
                                              @Param("userId") long userId,
                                              @Param("status") EnrollmentStatus status);

    List<StatusCount> countByStatusForUser(@Param("userId") long userId);
}
