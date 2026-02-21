package com.jacky.ai.skills.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skills 模块统一异常输出，避免前端/网关面对不稳定错误结构。
 */
@RestControllerAdvice
public class SkillExceptionHandler {

    @ExceptionHandler(SkillException.class)
    public ResponseEntity<Map<String, Object>> handleSkillException(SkillException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
                                                                     HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("code", "INVALID_ARGUMENT");
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }
}
