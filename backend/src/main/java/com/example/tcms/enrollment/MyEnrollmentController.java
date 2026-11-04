package com.example.tcms.enrollment;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.tcms.auth.CurrentUser;
import com.example.tcms.common.PageResult;
import com.example.tcms.enrollment.dto.MyEnrollmentVO;
import com.example.tcms.enrollment.dto.MyStatsVO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 我的报名与个人统计（技术设计 §7.4）。 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyEnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping("/enrollments")
    public PageResult<MyEnrollmentVO> myEnrollments(
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @AuthenticationPrincipal CurrentUser user) {
        return enrollmentService.listMine(user.id(), status, page, size);
    }

    @GetMapping("/stats")
    public MyStatsVO myStats(@AuthenticationPrincipal CurrentUser user) {
        return enrollmentService.myStats(user.id());
    }
}
