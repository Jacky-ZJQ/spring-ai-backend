package com.jacky.ai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 接口调用配额拦截器（内存版）。
 * <p>
 * 规则说明：
 * 1) 用户标识优先取自自定义请求头（默认 X-User-Id），没有则回退到 IP；
 * 2) 在滑动时间窗口内统计请求次数，超出阈值返回 429；
 * 3) 计数存在内存中，服务重启后会清空（适合单机部署）。
 */
@Slf4j
@Component
public class AiQuotaInterceptor implements HandlerInterceptor {

    /**
     * 每 128 次请求尝试清理一次过期用户，降低清理开销。
     */
    private static final long CLEANUP_INTERVAL_MASK = 127L;

    /**
     * 是否开启配额限制。
     */
    @Value("${spring.ai.quota.enabled:true}")
    private boolean enabled;

    /**
     * 单个窗口内允许的最大请求次数。
     */
    @Value("${spring.ai.quota.max-requests-per-window:60}")
    private int maxRequestsPerWindow;

    /**
     * 配额窗口大小（秒）。
     */
    @Value("${spring.ai.quota.window-seconds:3600}")
    private long windowSeconds;

    /**
     * 优先用于识别用户身份的请求头。
     */
    @Value("${spring.ai.quota.user-header:X-User-Id}")
    private String userHeader;

    /**
     * 超限时返回给前端的提示文案。
     */
    @Value("${spring.ai.quota.error-message:模型调用次数已达上限，请稍后再试。}")
    private String errorMessage;

    /**
     * key: 用户标识（user:xxx / ip:xxx）
     * value: 该用户在窗口内的请求时间戳队列（毫秒）。
     */
    private final Map<String, Deque<Long>> requestCounter = new ConcurrentHashMap<>();

    /**
     * 记录用户最近一次访问时间，用于惰性清理。
     */
    private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    /**
     * 全局计数器，用于控制清理频率。
     */
    private final AtomicLong totalCheckCounter = new AtomicLong(0);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 关闭配额或预检请求时，直接放行。
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        int safeMaxRequests = Math.max(1, maxRequestsPerWindow);
        long windowMillis = Math.max(1, windowSeconds) * 1000L;
        long now = System.currentTimeMillis();
        String userKey = resolveUserKey(request);
        Deque<Long> timestamps = requestCounter.computeIfAbsent(userKey, k -> new ArrayDeque<>());

        // 同一用户的时间戳队列需要串行修改，避免并发下统计错误。
        synchronized (timestamps) {
            clearExpired(timestamps, now, windowMillis);

            if (timestamps.size() >= safeMaxRequests) {
                long retryAfterSeconds = calculateRetryAfterSeconds(timestamps, now, windowMillis);
                writeLimitedResponse(response, safeMaxRequests, retryAfterSeconds);
                log.warn("AI quota exceeded, userKey={}, path={}, limit={}, windowSeconds={}",
                        userKey, request.getRequestURI(), safeMaxRequests, windowSeconds);
                return false;
            }

            // 未超限，将当前请求写入窗口队列。
            timestamps.addLast(now);
        }

        lastAccessTime.put(userKey, now);
        cleanupStaleUsers(now, windowMillis);
        return true;
    }

    /**
     * 清理窗口外的历史请求时间戳。
     */
    private void clearExpired(Deque<Long> timestamps, long now, long windowMillis) {
        long threshold = now - windowMillis;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= threshold) {
            timestamps.pollFirst();
        }
    }

    /**
     * 计算客户端最早可重试时间（秒）。
     */
    private long calculateRetryAfterSeconds(Deque<Long> timestamps, long now, long windowMillis) {
        Long oldestRequest = timestamps.peekFirst();
        if (oldestRequest == null) {
            return 1L;
        }
        long remainMillis = oldestRequest + windowMillis - now;
        return Math.max(1L, (remainMillis + 999L) / 1000L);
    }

    /**
     * 统一输出超限响应，前端可直接根据 retryAfterSeconds 做倒计时提示。
     */
    private void writeLimitedResponse(HttpServletResponse response, int limit, long retryAfterSeconds) throws Exception {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", "0");

        String safeMessage = errorMessage == null ? "" : errorMessage.replace("\"", "\\\"");
        String body = "{\"ok\":0,\"msg\":\"" + safeMessage + "\",\"retryAfterSeconds\":" + retryAfterSeconds + "}";
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    /**
     * 惰性清理长期未访问用户，防止内存无限增长。
     */
    private void cleanupStaleUsers(long now, long windowMillis) {
        long count = totalCheckCounter.incrementAndGet();
        if ((count & CLEANUP_INTERVAL_MASK) != 0) {
            return;
        }

        long expireBefore = now - (windowMillis * 2);
        lastAccessTime.entrySet().removeIf(entry -> {
            if (entry.getValue() >= expireBefore) {
                return false;
            }
            requestCounter.remove(entry.getKey());
            return true;
        });
    }

    /**
     * 用户标识解析优先级：
     * 1) 自定义 user-header
     * 2) X-Forwarded-For
     * 3) X-Real-IP
     * 4) request.getRemoteAddr()
     */
    private String resolveUserKey(HttpServletRequest request) {
        String headerValue = request.getHeader(userHeader);
        if (StringUtils.hasText(headerValue)) {
            return "user:" + headerValue.trim();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String ip = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(ip)) {
                return "ip:" + ip;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return "ip:" + realIp.trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * 手动清空配额计数（用于运维释放内存）。
     *
     * @return 清空前的用户计数
     */
    public int clearAllCounters() {
        int userCount = requestCounter.size();
        requestCounter.clear();
        lastAccessTime.clear();
        totalCheckCounter.set(0);
        return userCount;
    }
}
