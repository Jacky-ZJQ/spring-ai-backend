package com.jacky.ai.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 配置入口。
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpConfiguration {
}
