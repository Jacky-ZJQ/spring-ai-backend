package com.jacky.ai.mcp.tool;

import java.util.Map;

/**
 * MCP 工具执行器接口。
 */
public interface McpToolHandler {

    /**
     * 工具名，必须与配置 app.mcp.tools[*].name 对齐。
     */
    String name();

    /**
     * 执行工具。
     *
     * @param arguments MCP tools/call.arguments
     * @return 工具执行结果（String/Map/List/POJO）
     */
    Object execute(Map<String, Object> arguments);
}
