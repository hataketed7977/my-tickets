package com.bytedance.tickets.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApiException(ApiException error) {
        return error(error.status(), error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException error
    ) {
        var fieldErrors = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                )
        );
        return error(HttpStatus.BAD_REQUEST, "请求参数校验失败", fieldErrors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> handleConflict(
            DataIntegrityViolationException error
    ) {
        return error(HttpStatus.CONFLICT, "数据已存在或关联关系无效");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(
            NoResourceFoundException error
    ) {
        return error(HttpStatus.NOT_FOUND, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception error) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String message
    ) {
        return error(status, message, null);
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String message,
            Map<String, String> fieldErrors
    ) {
        var details = new LinkedHashMap<String, Object>();
        details.put("code", status.name());
        details.put("message", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            details.put("fieldErrors", fieldErrors);
        }
        details.put("timestamp", Instant.now().toEpochMilli());
        return ResponseEntity.status(status).body(Map.of("error", details));
    }
}
