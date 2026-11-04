package com.example.tcms.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.example.tcms.user.dto.CreateUserRequest;
import com.example.tcms.user.dto.ResetPasswordRequest;
import com.example.tcms.user.dto.UpdateEnabledRequest;
import com.example.tcms.user.dto.UserVO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 管理员用户管理（技术设计 §4.2、§7.2）。 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @GetMapping
    public PageResult<UserVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return userService.page(keyword, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserVO create(@Valid @RequestBody CreateUserRequest request) {
        return UserVO.from(userService.create(request.username(), request.password(),
                request.realName(), request.department(), request.role()));
    }

    @PatchMapping("/{id}/enabled")
    public UserVO updateEnabled(@PathVariable long id, @Valid @RequestBody UpdateEnabledRequest request,
                                @AuthenticationPrincipal CurrentUser operator) {
        return UserVO.from(userService.setEnabled(id, request.enabled(), operator.id()));
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.updatePassword(id, request.newPassword());
    }
}
