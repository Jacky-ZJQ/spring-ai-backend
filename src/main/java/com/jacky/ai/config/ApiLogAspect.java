package com.jacky.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * @author: Jacky.Zhang
 * @date: 2025/6/21 10:04
 * @description： API日志切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiLogAspect {

    private final ObjectMapper objectMapper;

    @Pointcut("execution(* com.jacky.ai.controller..*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String url = request != null ? request.getRequestURL().toString() : "unknown";
        String method = request != null ? request.getMethod() : "unknown";
        String ip = request != null ? getIpAddress(request) : "unknown";
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        String params = formatParams(joinPoint);
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);

        log.info("[API-REQ] {} {} | {}.{} | IP: {} | Params: {}", method, url, simpleClassName, methodName, ip, params);

        Object result = null;
        try {
            result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            String response = formatResponse(result);
            log.info("[API-RES] {} {} | {}.{} | Cost: {}ms | Response: {}", method, url, simpleClassName, methodName, duration, response);
            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("[API-ERR] {} {} | {}.{} | Cost: {}ms | Error: {}", method, url, simpleClassName, methodName, duration, e.getMessage(), e);
            throw e;
        }
    }

    private String formatParams(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "{}";
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Map<String, Object> paramMap = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            String paramName = paramNames != null && i < paramNames.length ? paramNames[i] : "arg" + i;
            Object arg = args[i];

            if (arg instanceof MultipartFile) {
                paramMap.put(paramName, "[MultipartFile: " + ((MultipartFile) arg).getOriginalFilename() + "]");
            } else if (arg instanceof MultipartFile[]) {
                MultipartFile[] files = (MultipartFile[]) arg;
                List<String> fileNames = new ArrayList<>();
                for (MultipartFile file : files) {
                    fileNames.add(file.getOriginalFilename());
                }
                paramMap.put(paramName, "[MultipartFile[]: " + fileNames + "]");
            } else {
                paramMap.put(paramName, arg);
            }
        }

        try {
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            return paramMap.toString();
        }
    }

    private String formatResponse(Object result) {
        if (result == null) {
            return "null";
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}