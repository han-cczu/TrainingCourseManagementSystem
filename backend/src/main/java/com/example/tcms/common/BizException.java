package com.example.tcms.common;

import java.util.Map;

import lombok.Getter;

/** 可预期的业务异常，由 {@link GlobalExceptionHandler} 转换为对应 HTTP 状态和错误体。 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BizException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
