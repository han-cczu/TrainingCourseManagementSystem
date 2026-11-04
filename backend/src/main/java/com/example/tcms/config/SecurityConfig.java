package com.example.tcms.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.example.tcms.auth.JwtAuthConverter;

/**
 * 无状态 JWT 认证（技术设计 §4）。
 * <p>
 * 不使用 Cookie，因此关闭 CSRF；除注册、登录、健康检查和 API 文档外，所有接口都需要登录；
 * 管理员接口在控制器上用 {@code @PreAuthorize("hasRole('ADMIN')")} 控制。
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthConverter jwtAuthConverter,
                                                   JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用独立版本标识，后续更改参数时保留旧版本的校验能力。
        String id = "pbkdf2-sha256-v1";
        var pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        // 16 字节盐 + 32 字节哈希，Base64 加版本前缀共 82 字符，适配现有 VARCHAR(100)。
        pbkdf2.setEncodeHashAsBase64(true);
        var bcrypt = new BCryptPasswordEncoder();
        var encoder = new DelegatingPasswordEncoder(id, Map.of(id, pbkdf2, "bcrypt", bcrypt));
        // 原有管理员、员工及演示数据使用无前缀 BCrypt，继续支持登录和原密码验证。
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
}
