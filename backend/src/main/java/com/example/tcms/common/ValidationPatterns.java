package com.example.tcms.common;

/** 用户名/密码规则（技术设计 §4.3），前端 src/utils/validation.ts 保持一致。 */
public final class ValidationPatterns {

    public static final String USERNAME = "^[A-Za-z0-9_]{3,32}$";
    public static final String USERNAME_MESSAGE = "用户名需为 3-32 位字母、数字或下划线";

    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE = "密码需为 8-64 位，且同时包含字母和数字";

    private ValidationPatterns() {
    }
}
