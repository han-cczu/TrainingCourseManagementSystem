package com.example.tcms.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务代码获取"当前时间"的唯一来源（技术设计 §5.4）。
 * <p>
 * 固定业务时区，使"是否已开课"的判断不受容器/JVM 默认时区影响；测试中可替换为 {@link Clock#fixed}。
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Shanghai}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
