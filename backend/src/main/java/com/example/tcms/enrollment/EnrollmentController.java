package com.example.tcms.enrollment;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.tcms.auth.CurrentUser;
import com.example.tcms.common.PageResult;
import com.example.tcms.enrollment.dto.CourseEnrollmentVO;
import com.example.tcms.enrollment.dto.EnrollmentVO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 报名相关接口（技术设计 §7.4）。/me 接口只操作当前用户自己的报名。 */
@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/courses/{courseId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentVO enroll(@PathVariable long courseId, @AuthenticationPrincipal CurrentUser user) {
        return enrollmentService.enroll(courseId, user);
    }

    @DeleteMapping("/api/courses/{courseId}/enrollments/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long courseId, @AuthenticationPrincipal CurrentUser user) {
        enrollmentService.cancel(courseId, user);
    }

    @PostMapping("/api/courses/{courseId}/enrollments/me/complete")
    public EnrollmentVO completeSelf(@PathVariable long courseId, @AuthenticationPrincipal CurrentUser user) {
        return enrollmentService.completeSelf(courseId, user);
    }

    @GetMapping("/api/courses/{courseId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<CourseEnrollmentVO> listByCourse(
            @PathVariable long courseId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return enrollmentService.listByCourse(courseId, status, page, size);
    }

    @PostMapping("/api/enrollments/{enrollmentId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public EnrollmentVO completeByAdmin(@PathVariable long enrollmentId, @AuthenticationPrincipal CurrentUser admin) {
        return enrollmentService.completeByAdmin(enrollmentId, admin);
    }
}
