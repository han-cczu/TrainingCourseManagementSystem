package com.example.tcms.auth;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.example.tcms.user.User;
import com.example.tcms.user.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 把已通过签名/过期校验的 JWT 转成当前用户（技术设计 §4.4）。
 * <p>
 * 每次请求按主键查一次用户：用户被删除或停用时立即返回 401，角色以数据库为准而非 token 中的声明。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserMapper userMapper;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !user.isActive()) {
            throw new InvalidBearerTokenException("User not found or disabled");
        }
        CurrentUser principal = new CurrentUser(user.getId(), user.getUsername(), user.getRole());
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }
}
