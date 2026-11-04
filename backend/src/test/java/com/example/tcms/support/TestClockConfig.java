package com.example.tcms.support;

import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 用 {@link MutableClock} 替换业务 Clock（@Primary），所有依赖 Clock 的组件都会使用它。 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(ZoneId.of("Asia/Shanghai"));
    }
}
