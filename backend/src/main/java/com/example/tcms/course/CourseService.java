package com.example.tcms.course;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;
import com.example.tcms.common.PageResult;
import com.example.tcms.course.dto.CourseRequest;
import com.example.tcms.course.dto.CourseVO;

import lombok.RequiredArgsConstructor;

/** 课程增改查与时间规则（技术设计 §5.3）。 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final Clock clock;

    public CourseVO create(CourseRequest request, long operatorId) {
        LocalDateTime now = now();
        Times times = normalizeTimes(request);
        requireFutureStart(times.start(), now);

        Course course = new Course();
        apply(course, request, times);
        course.setEnrolledCount(0);
        course.setCreatedBy(operatorId);
        courseMapper.insert(course);
        return CourseVO.from(course, now);
    }

    /**
     * 编辑课程。先锁课程行，避免与并发报名同时修改 enrolled_count / capacity。
     * 已开课：不能改开始/结束时间和容量（与原值相同则允许），其它字段可改。
     */
    @Transactional
    public CourseVO update(long id, CourseRequest request, long operatorId) {
        Course course = courseMapper.selectByIdForUpdate(id);
        if (course == null) {
            throw new BizException(ErrorCode.COURSE_NOT_FOUND);
        }
        LocalDateTime now = now();
        Times times = normalizeTimes(request);

        boolean started = !now.isBefore(course.getStartTime());
        if (started) {
            boolean scheduleChanged = !times.start().equals(course.getStartTime())
                    || !times.end().equals(course.getEndTime())
                    || !Objects.equals(request.capacity(), course.getCapacity());
            if (scheduleChanged) {
                throw new BizException(ErrorCode.COURSE_STARTED, "课程已开始，不能修改时间和容量");
            }
        } else {
            requireFutureStart(times.start(), now);
        }
        if (request.capacity() != null && request.capacity() < course.getEnrolledCount()) {
            throw new BizException(ErrorCode.CAPACITY_BELOW_ENROLLED,
                    "容量不能小于已报名人数（" + course.getEnrolledCount() + "）",
                    Map.of("enrolledCount", course.getEnrolledCount()));
        }

        apply(course, request, times);
        courseMapper.updateById(course);
        return detail(id, operatorId);
    }

    public CourseVO detail(long id, long currentUserId) {
        Course course = courseMapper.selectCourseDetail(id, currentUserId);
        if (course == null) {
            throw new BizException(ErrorCode.COURSE_NOT_FOUND);
        }
        return CourseVO.from(course, now());
    }

    public PageResult<CourseVO> page(String keyword, CoursePhase phase, long page, long size, long currentUserId) {
        LocalDateTime now = now();
        var result = courseMapper.selectCoursePage(new Page<>(page, size),
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                phase == null ? null : phase.name(),
                now, currentUserId);
        return PageResult.of(result, c -> CourseVO.from(c, now));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /** DATETIME 精确到秒（MySQL 会对小数秒四舍五入），先截断再比较，避免"未修改却被判为修改" */
    private static Times normalizeTimes(CourseRequest request) {
        LocalDateTime start = request.startTime().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime end = request.endTime().truncatedTo(ChronoUnit.SECONDS);
        if (!end.isAfter(start)) {
            throw validation("endTime", "结束时间必须晚于开始时间");
        }
        return new Times(start, end);
    }

    private static void requireFutureStart(LocalDateTime start, LocalDateTime now) {
        if (!start.isAfter(now)) {
            throw validation("startTime", "开始时间必须晚于当前时间");
        }
    }

    private static BizException validation(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_ERROR, message, Map.of("fields", Map.of(field, message)));
    }

    private static void apply(Course course, CourseRequest request, Times times) {
        course.setTitle(request.title().trim());
        course.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        course.setInstructor(request.instructor().trim());
        course.setLocation(StringUtils.hasText(request.location()) ? request.location().trim() : null);
        course.setStartTime(times.start());
        course.setEndTime(times.end());
        course.setCapacity(request.capacity());
    }

    private record Times(LocalDateTime start, LocalDateTime end) {
    }
}
