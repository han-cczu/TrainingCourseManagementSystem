package com.example.tcms.auth;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.tcms.auth.dto.ChangePasswordRequest;
import com.example.tcms.auth.dto.LoginRequest;
import com.example.tcms.auth.dto.LoginResponse;
import com.example.tcms.auth.dto.RegisterRequest;
import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;
import com.example.tcms.user.Role;
import com.example.tcms.user.User;
import com.example.tcms.user.UserService;
import com.example.tcms.user.dto.UserVO;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    /** 用户不存在时也做一次密码哈希比对，避免直接返回造成明显的耗时差异。 */
    private final String dummyHash;

    public AuthService(UserService userService, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    public UserVO register(RegisterRequest request) {
        User user = userService.create(request.username(), request.password(),
                request.realName(), request.department(), Role.EMPLOYEE);
        return UserVO.from(user);
    }

    /** 用户不存在、密码错误、账号停用一律返回 BAD_CREDENTIALS（技术设计 §4.3）。 */
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = userService.findByUsername(request.username());
        boolean passwordOk = passwordEncoder.matches(request.password(),
                found.map(User::getPasswordHash).orElse(dummyHash));
        User user = found.filter(u -> passwordOk && u.isActive())
                .orElseThrow(() -> new BizException(ErrorCode.BAD_CREDENTIALS));
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), UserVO.from(user));
    }

    public UserVO me(CurrentUser currentUser) {
        return UserVO.from(userService.getById(currentUser.id()));
    }

    /** 原密码错误返回 400（而非 401），避免前端把它当作登录失效而跳转登录页。 */
    public void changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
        User user = userService.getById(currentUser.id());
        if (!userService.passwordMatches(user, request.oldPassword())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "原密码不正确",
                    Map.of("fields", Map.of("oldPassword", "原密码不正确")));
        }
        userService.updatePassword(user.getId(), request.newPassword());
    }
}
