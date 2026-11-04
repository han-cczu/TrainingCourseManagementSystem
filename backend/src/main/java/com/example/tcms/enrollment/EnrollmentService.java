package com.example.tcms.enrollment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.tcms.auth.CurrentUser;
import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;
import com.example.tcms.common.PageResult;
import com.example.tcms.course.Course;
import com.example.tcms.course.CourseMapper;
import com.example.tcms.course.CoursePhase;
import com.example.tcms.enrollment.dto.CourseEnrollmentVO;
import com.example.tcms.enrollment.dto.EnrollmentVO;
import com.example.tcms.enrollment.dto.MyEnrollmentVO;
import com.example.tcms.enrollment.dto.MyStatsVO;
import com.example.tcms.enrollment.dto.StatusCount;

import lombok.RequiredArgsConstructor;

/**
 * 报名、取消、完成（技术设计 §5.2、§6）。
 * <p>
 * <b>所有写操作的固定顺序：先锁课程行（SELECT ... FOR UPDATE），再读写报名记录。</b>
 * 同一门课的写操作因此串行执行：名额检查与计数更新之间不会被并发请求插入，
 * 统一的加锁顺序也避免了死锁。正确性只依赖数据库锁，后端多副本部署时依然成立。
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseMapper courseMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final Clock clock;

    @Transactional
    public EnrollmentVO enroll(long courseId, CurrentUser user) {
        Course course = lockCourse(courseId);
        LocalDateTime now = now();
        if (!now.isBefore(course.getStartTime())) {
            throw new BizException(ErrorCode.COURSE_STARTED, "课程已开始，不能报名");
        }
        Enrollment enrollment = findByCourseAndUser(courseId, user.id());
        if (enrollment != null && enrollment.occupiesSeat()) {
            throw new BizException(ErrorCode.ALREADY_ENROLLED);
        }
        if (course.getCapacity() != null && course.getEnrolledCount() >= course.getCapacity()) {
            throw new BizException(ErrorCode.COURSE_FULL);
        }

        if (enrollment == null) {
            enrollment = Enrollment.newEnrolled(courseId, user.id(), now);
            try {
                enrollmentMapper.insert(enrollment);
            } catch (DuplicateKeyException e) {
                // 唯一索引兜底：正常情况下课程行锁已经保证不会走到这里
                throw new BizException(ErrorCode.ALREADY_ENROLLED);
            }
        } else {
            enrollment.reEnroll(now);
            enrollmentMapper.updateById(enrollment);
        }
        courseMapper.incrementEnrolledCount(courseId, 1, now);
        return EnrollmentVO.from(enrollment);
    }

    @Transactional
    public void cancel(long courseId, CurrentUser user) {
        Course course = lockCourse(courseId);
        LocalDateTime now = now();
        if (!now.isBefore(course.getStartTime())) {
            throw new BizException(ErrorCode.COURSE_STARTED, "课程已开始，不能取消报名");
        }
        Enrollment enrollment = requireOwnEnrollment(courseId, user.id());
        enrollment.cancel(now);
        enrollmentMapper.updateById(enrollment);
        courseMapper.incrementEnrolledCount(courseId, -1, now);
    }

    /** 本人标记学习完成；完成不改变已报名人数（完成的人仍占名额）。 */
    @Transactional
    public EnrollmentVO completeSelf(long courseId, CurrentUser user) {
        Course course = lockCourse(courseId);
        LocalDateTime now = now();
        requireStarted(course, now);
        Enrollment enrollment = requireOwnEnrollment(courseId, user.id());
        enrollment.complete(now, user.id());
        enrollmentMapper.updateById(enrollment);
        return EnrollmentVO.from(enrollment);
    }

    /** 管理员给某条报名标记完成。 */
    @Transactional
    public EnrollmentVO completeByAdmin(long enrollmentId, CurrentUser admin) {
        Enrollment unlocked = enrollmentMapper.selectById(enrollmentId);
        if (unlocked == null) {
            throw new BizException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        Course course = lockCourse(unlocked.getCourseId());
        // 锁后必须当前读：普通 selectById 可能命中 MyBatis 缓存或 MySQL 的旧快照。
        Enrollment enrollment = enrollmentMapper.selectByIdForUpdate(enrollmentId);
        LocalDateTime now = now();
        requireStarted(course, now);
        enrollment.complete(now, admin.id());
        enrollmentMapper.updateById(enrollment);
        return EnrollmentVO.from(enrollment);
    }

    public PageResult<CourseEnrollmentVO> listByCourse(long courseId, EnrollmentStatus status, long page, long size) {
        if (courseMapper.selectById(courseId) == null) {
            throw new BizException(ErrorCode.COURSE_NOT_FOUND);
        }
        return PageResult.of(enrollmentMapper.selectCourseEnrollments(new Page<>(page, size), courseId, status));
    }

    public PageResult<MyEnrollmentVO> listMine(long userId, EnrollmentStatus status, long page, long size) {
        LocalDateTime now = now();
        var result = enrollmentMapper.selectMyEnrollments(new Page<>(page, size), userId, status);
        result.getRecords().forEach(r -> r.setPhase(CoursePhase.of(r.getStartTime(), r.getEndTime(), now)));
        return PageResult.of(result);
    }

    public MyStatsVO myStats(long userId) {
        Map<EnrollmentStatus, Long> counts = new EnumMap<>(EnrollmentStatus.class);
        for (StatusCount row : enrollmentMapper.countByStatusForUser(userId)) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return new MyStatsVO(counts.getOrDefault(EnrollmentStatus.ENROLLED, 0L),
                counts.getOrDefault(EnrollmentStatus.COMPLETED, 0L),
                counts.getOrDefault(EnrollmentStatus.CANCELLED, 0L));
    }

    private Course lockCourse(long courseId) {
        Course course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    private Enrollment findByCourseAndUser(long courseId, long userId) {
        return enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getUserId, userId));
    }

    private Enrollment requireOwnEnrollment(long courseId, long userId) {
        Enrollment enrollment = findByCourseAndUser(courseId, userId);
        if (enrollment == null) {
            throw new BizException(ErrorCode.ENROLLMENT_NOT_FOUND, "你还没有报名该课程");
        }
        return enrollment;
    }

    private static void requireStarted(Course course, LocalDateTime now) {
        if (now.isBefore(course.getStartTime())) {
            throw new BizException(ErrorCode.COURSE_NOT_STARTED);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
