package com.example.tcms.user;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;
import com.example.tcms.common.PageResult;
import com.example.tcms.user.dto.UserVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 创建用户；用户名唯一由数据库唯一索引保证（不区分大小写），冲突时返回 USERNAME_TAKEN。 */
    public User create(String username, String rawPassword, String realName, String department, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setDepartment(StringUtils.hasText(department) ? department : null);
        user.setRole(role);
        user.setEnabled(true);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.USERNAME_TAKEN);
        }
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)));
    }

    public User getById(long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public boolean adminExists() {
        return userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getRole, Role.ADMIN));
    }

    public PageResult<UserVO> page(String keyword, long page, long size) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getRealName, keyword))
                .orderByDesc(User::getId);
        return PageResult.of(userMapper.selectPage(new Page<>(page, size), query), UserVO::from);
    }

    @Transactional
    public User setEnabled(long id, boolean enabled, long operatorId) {
        if (!enabled && id == operatorId) {
            throw new BizException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        User user = getById(id);
        user.setEnabled(enabled);
        userMapper.updateById(user);
        return user;
    }

    @Transactional
    public void updatePassword(long id, String rawPassword) {
        User user = getById(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userMapper.updateById(user);
    }

    public boolean passwordMatches(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }
}
