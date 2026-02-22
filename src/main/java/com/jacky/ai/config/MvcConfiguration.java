package com.jacky.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@RequiredArgsConstructor
@Configuration
public class MvcConfiguration implements WebMvcConfigurer {

    private final AiQuotaInterceptor aiQuotaInterceptor;

    /**
     * 需要启用 AI 配额限制的接口路径，多个路径用英文逗号分隔。
     */
    @Value("${spring.ai.quota.path-patterns:/ai/chat,/ai/game,/ai/service,/ai/pdf/chat}")
    private String quotaPathPatterns;

    /**
     * 解决跨域问题
     * @param registry 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 允许通过配置灵活增减限流接口，避免写死在代码中。
        String[] patterns = Arrays.stream(quotaPathPatterns.split(","))
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .toArray(String[]::new);
        if (patterns.length == 0) {
            return;
        }
        registry.addInterceptor(aiQuotaInterceptor).addPathPatterns(patterns);
    }
}
