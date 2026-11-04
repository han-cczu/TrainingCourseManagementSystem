package com.example.tcms;

import org.junit.jupiter.api.Test;

import com.example.tcms.support.AbstractIntegrationTest;

/** 冒烟测试：上下文能启动、Flyway 迁移能在 MySQL 8.4 上成功执行。 */
class TcmsApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
