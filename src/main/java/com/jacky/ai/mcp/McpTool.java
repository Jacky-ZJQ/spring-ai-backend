package com.jacky.ai.mcp;

import java.util.Map;

/**
 * MCP 工具统一契约。
 */
public interface McpTool {

    /**
     * 对外暴露给 MCP 客户端的唯一工具名。
     */
    String name();

    /**
     * 在 tools/list 中展示的工具说明。
     */
    String description();

    /**
     * 工具参数的 JSON Schema 定义。
     */
    Map<String, Object> inputSchema();

    /**
     * 使用解析后的参数执行工具。
     */
    Object execute(Map<String, Object> arguments);
}
