package com.jacky.ai.config;

import com.jacky.ai.util.ResponseUtil;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理应用中的所有异常，返回标准格式的错误响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有未捕获的异常
     * @param ex 异常对象
     * @return 包含错误信息的响应Map
     */
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception ex) {
        return ResponseUtil.fail(ex.getMessage());
    }
}