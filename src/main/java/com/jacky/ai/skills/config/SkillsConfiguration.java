package com.jacky.ai.skills.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Skills 配置入口。
 */
@Configuration
@EnableConfigurationProperties(SkillsProperties.class)
public class SkillsConfiguration {
}
