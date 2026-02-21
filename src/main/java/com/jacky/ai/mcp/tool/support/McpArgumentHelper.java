package com.jacky.ai.mcp.tool.support;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * MCP 工具参数解析辅助类。
 */
public final class McpArgumentHelper {

    private McpArgumentHelper() {
    }

    public static String requiredString(Map<String, Object> arguments, String fieldName) {
        String value = nullableString(arguments.get(fieldName));
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    public static String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : null;
    }
}
