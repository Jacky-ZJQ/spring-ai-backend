package com.jacky.ai.skills.exception;

import org.springframework.http.HttpStatus;

/**
 * Skills 模块统一业务异常。
 */
public class SkillException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public SkillException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
