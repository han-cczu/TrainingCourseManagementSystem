package com.example.tcms.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/** 把异常统一转换成 {@link ErrorResponse}（技术设计 §7.1、§7.6）。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorResponse> handleBiz(BizException ex) {
        return build(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
    }

    /** @RequestBody 上的 Bean Validation 失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return validationError(fields);
    }

    /** @RequestParam / @PathVariable 上的约束校验失败 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> fields.putIfAbsent(
                    name == null ? "param" : name, error.getDefaultMessage()));
        });
        return validationError(fields);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "请求格式不正确", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(), null);
    }

    /** 兜底：不把内部异常信息暴露给前端，只记录日志 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    private ResponseEntity<ErrorResponse> validationError(Map<String, Object> fields) {
        String message = fields.values().stream()
                .filter(v -> v != null)
                .map(Object::toString)
                .findFirst()
                .orElse(ErrorCode.VALIDATION_ERROR.getDefaultMessage());
        return build(ErrorCode.VALIDATION_ERROR, message, Map.of("fields", fields));
    }

    private static ResponseEntity<ErrorResponse> build(ErrorCode code, String message, Map<String, Object> details) {
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(code.name(), message, details));
    }
}
