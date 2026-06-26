package com.takeout.common.exception;

import com.takeout.common.result.Result;
import com.takeout.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        String msg = "系统繁忙，请稍后重试";
        String em = e.getMessage();
        if (em != null) {
            if (em.contains("DataAccess") || em.contains("DataSource") || em.contains("CommunicationsException") || em.contains("MySQL") || em.contains("Connection refused") && em.contains("3306")) {
                msg = "数据库连接失败，请检查 MySQL 是否已启动（端口 3306）";
            } else if (em.contains("Redis") || em.contains("redis") || em.contains("connection refused") && em.contains("6379")) {
                msg = "Redis 连接失败，请检查 Redis 是否已启动（端口 6379）";
            } else if (em.contains("Cache") || em.contains("deserialize") || em.contains("MismatchedInputException")) {
                msg = "Redis 缓存数据异常，请执行 flushdb 清理缓存后重试";
            }
        }
        return Result.fail(ResultCode.FAIL, msg);
    }
}
