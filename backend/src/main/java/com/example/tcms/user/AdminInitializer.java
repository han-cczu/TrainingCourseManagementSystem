package com.example.tcms.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;
import com.example.tcms.config.AdminProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动时若库中没有管理员，则按配置创建（技术设计 §4.2）。
 * 多副本同时启动可能并发创建，后到者会撞唯一索引，忽略即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserService userService;
    private final AdminProperties adminProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (userService.adminExists()) {
            return;
        }
        try {
            userService.create(adminProperties.username(), adminProperties.password(), "系统管理员", null, Role.ADMIN);
            log.info("已创建初始管理员账号：{}", adminProperties.username());
        } catch (BizException e) {
            if (e.getErrorCode() != ErrorCode.USERNAME_TAKEN) {
                throw e;
            }
            log.warn("初始管理员用户名 {} 已存在，跳过创建", adminProperties.username());
        }
    }
}
