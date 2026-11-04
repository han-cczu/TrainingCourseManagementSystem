package com.example.tcms.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 业务错误码（技术设计 §7.6）。code 字符串即枚举名，前端可据此做分支处理。 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    CAPACITY_BELOW_ENROLLED(HttpStatus.BAD_REQUEST, "课程容量不能小于已报名人数"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限执行该操作"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "课程不存在"),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "报名记录不存在"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的请求内容类型，请使用 application/json"),

    USERNAME_TAKEN(HttpStatus.CONFLICT, "用户名已被占用"),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "你已报名该课程"),
    COURSE_FULL(HttpStatus.CONFLICT, "课程名额已满"),
    COURSE_STARTED(HttpStatus.CONFLICT, "课程已开始，无法执行该操作"),
    COURSE_NOT_STARTED(HttpStatus.CONFLICT, "课程尚未开始，暂不能标记完成"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "当前报名状态不允许该操作"),
    CANNOT_DISABLE_SELF(HttpStatus.CONFLICT, "不能停用自己的账号"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试");

    private final HttpStatus status;
    private final String defaultMessage;
}
