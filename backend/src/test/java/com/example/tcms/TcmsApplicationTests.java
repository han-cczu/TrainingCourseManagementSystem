package com.example.tcms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 冒烟测试：上下文能启动、Flyway 迁移能在 MySQL 8.4 上成功执行。 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class TcmsApplicationTests {

    @Test
    void contextLoads() {
    }
}
