package com.example.tcms.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 测试用可调时钟：每个测试前重置为 {@link #DEFAULT_NOW}，可前后拨动以验证时间规则和 token 过期。 */
public class MutableClock extends Clock {

    public static final LocalDateTime DEFAULT_NOW = LocalDateTime.of(2026, 11, 2, 9, 0, 0);

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
        reset();
    }

    public void reset() {
        setNow(DEFAULT_NOW);
    }

    public void setNow(LocalDateTime now) {
        this.instant = now.atZone(zone).toInstant();
    }

    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant, zone);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return Clock.fixed(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
