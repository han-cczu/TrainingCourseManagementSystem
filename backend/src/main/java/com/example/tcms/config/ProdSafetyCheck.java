package com.example.tcms.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 生产环境启动自检：仍在使用 application.yml 中的开发默认密钥/密码时拒绝启动，防止带着默认值上线。
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSafetyCheck implements InitializingBean {

    /** 与 application.yml 中的开发默认值保持一致 */
    static final String DEV_JWT_SECRET_PREFIX = "dev-only-";
    static final String DEV_ADMIN_PASSWORD = "Admin12345";

    private final JwtProperties jwtProperties;
    private final AdminProperties adminProperties;

    @Override
    public void afterPropertiesSet() {
        if (jwtProperties.secret().startsWith(DEV_JWT_SECRET_PREFIX)) {
            throw new IllegalStateException("生产环境必须通过 JWT_SECRET 设置随机密钥，不能使用开发默认值");
        }
        if (DEV_ADMIN_PASSWORD.equals(adminProperties.password())) {
            throw new IllegalStateException("生产环境必须通过 ADMIN_PASSWORD 设置管理员初始密码，不能使用开发默认值");
        }
    }
}
