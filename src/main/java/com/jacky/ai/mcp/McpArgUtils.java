package com.jacky.ai.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具入参的轻量解析工具类。
 */
public final class McpArgUtils {

    private McpArgUtils() {
    }

    public static String requiredString(Map<String, Object> args, String key) {
        String value = optionalString(args, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value;
    }

    public static String optionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return String.valueOf(value).trim();
    }

    public static Integer optionalInt(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        // JSON 参数可能是数值节点或字符串，这里都兼容。
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Argument '" + key + "' must be an integer");
            }
        }
        throw new IllegalArgumentException("Argument '" + key + "' must be an integer");
    }

    public static Boolean optionalBoolean(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if ("true".equalsIgnoreCase(trimmed)) {
                return true;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        throw new IllegalArgumentException("Argument '" + key + "' must be a boolean");
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> optionalObjectList(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return List.of();
        }
        // 严格要求对象数组，避免后续动态转换产生歧义。
        if (!(value instanceof List<?> listValue)) {
            throw new IllegalArgumentException("Argument '" + key + "' must be an array");
        }
        List<Map<String, Object>> result = new ArrayList<>(listValue.size());
        for (Object item : listValue) {
            if (!(item instanceof Map<?, ?> mapItem)) {
                throw new IllegalArgumentException("Argument '" + key + "' contains non-object item");
            }
            result.add((Map<String, Object>) mapItem);
        }
        return result;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
