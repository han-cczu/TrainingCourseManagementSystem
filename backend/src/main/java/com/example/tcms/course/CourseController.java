package com.example.tcms.course;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.tcms.auth.CurrentUser;
import com.example.tcms.common.PageResult;
import com.example.tcms.course.dto.CourseRequest;
import com.example.tcms.course.dto.CourseVO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 课程（技术设计 §7.3）：所有登录用户可查看，管理员可创建和编辑。 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public PageResult<CourseVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CoursePhase phase,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return courseService.page(keyword, phase, page, size, currentUser.id());
    }

    @GetMapping("/{id}")
    public CourseVO detail(@PathVariable long id, @AuthenticationPrincipal CurrentUser currentUser) {
        return courseService.detail(id, currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CourseVO create(@Valid @RequestBody CourseRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return courseService.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseVO update(@PathVariable long id, @Valid @RequestBody CourseRequest request,
                           @AuthenticationPrincipal CurrentUser currentUser) {
        return courseService.update(id, request, currentUser.id());
    }
}
