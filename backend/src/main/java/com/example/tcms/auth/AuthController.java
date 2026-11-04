package com.example.tcms.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.tcms.auth.dto.ChangePasswordRequest;
import com.example.tcms.auth.dto.LoginRequest;
import com.example.tcms.auth.dto.LoginResponse;
import com.example.tcms.auth.dto.RegisterRequest;
import com.example.tcms.user.dto.UserVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 注册、登录、当前用户、修改密码（技术设计 §7.2）。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserVO register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserVO me(@AuthenticationPrincipal CurrentUser currentUser) {
        return authService.me(currentUser);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal CurrentUser currentUser,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser, request);
    }
}
