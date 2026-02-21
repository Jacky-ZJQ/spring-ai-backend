package com.jacky.ai.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 网关配置，建议放在 application*.yaml 中并通过环境变量覆盖。
 */
@Data
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    /**
     * 是否启用 MCP 网关。
     */
    private boolean enabled = true;

    /**
     * MCP 服务器名称（initialize 返回）。
     */
    private String serverName = "spring-ai-mcp-gateway";

    /**
     * MCP 服务器版本（initialize 返回）。
     */
    private String serverVersion = "1.0.0";

    /**
     * 会话空闲过期时间（秒）。
     */
    private long sessionTtlSeconds = 1800;

    /**
     * 按新到旧排序，initialize 进行协议协商时使用。
     */
    private List<String> protocolVersions = new ArrayList<>();

    /**
     * 当存在 @Tool 但未在 app.mcp.tools 中显式配置时，是否自动注册为 MCP 工具。
     */
    private boolean autoRegisterUnconfiguredTools = true;

    /**
     * MCP 工具定义配置，包含展示元信息和 JSON Schema。
     */
    private List<ToolDefinition> tools = new ArrayList<>();

    @Data
    public static class ToolDefinition {
        /**
         * 工具名称，必须与 McpToolHandler.name() 一致。
         */
        private String name;

        /**
         * 工具标题（可选）。
         */
        private String title;

        /**
         * 工具描述（可选）。
         */
        private String description;

        /**
         * 工具输入 schema（MCP tools/list 中返回）。
         */
        private Map<String, Object> inputSchema = new LinkedHashMap<>();

        /**
         * 只读提示。
         */
        private Boolean readOnlyHint;

        /**
         * 幂等提示。
         */
        private Boolean idempotentHint;

        /**
         * 工具是否启用。
         */
        private boolean enabled = true;
    }
}
