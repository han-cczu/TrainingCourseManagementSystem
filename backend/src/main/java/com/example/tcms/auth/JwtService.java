package com.example.tcms.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.tcms.config.JwtConfig;
import com.example.tcms.config.JwtProperties;
import com.example.tcms.user.User;

import lombok.RequiredArgsConstructor;

/** 签发 JWT：sub = 用户 ID，附带 username、role（role 仅供前端展示，鉴权以数据库为准）。 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, LocalDateTime.ofInstant(expiresAt, clock.getZone()));
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }
}
