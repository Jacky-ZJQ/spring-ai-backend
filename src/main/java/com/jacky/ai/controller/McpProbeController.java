package com.jacky.ai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacky.ai.mcp.McpTool;
import com.jacky.ai.mcp.McpToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 最小可用的 MCP HTTP 端点。
 * <p>
 * - 传输层：HTTP Streamable
 * - RPC 封装：JSON-RPC 2.0
 * - 能力：tools
 */
@RestController
@RequiredArgsConstructor
public class McpProbeController {

    private static final String MCP_PROTOCOL_VERSION = "2025-06-18";
    private static final String MCP_SESSION_ID = "spring-mcp-" + UUID.randomUUID();

    private final McpToolRegistry mcpToolRegistry;
    private final ObjectMapper objectMapper;

    @GetMapping("/mcp")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("ok", true, "name", "spring-mcp-probe"));
    }

    /**
     * MCP 单入口，处理全部 JSON-RPC 方法。
     */
    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> probe(@RequestBody(required = false) Map<String, Object> body) {
        Object id = body == null ? null : body.get("id");
        String method = body == null ? null : (String) body.get("method");

        if (method == null) {
            return withMcpHeaders(ResponseEntity.ok(error(id, -32600, "Invalid Request")));
        }

        return switch (method) {
            case "initialize" -> withMcpHeaders(ResponseEntity.ok(initialize(id, body)));
            case "notifications/initialized" -> withMcpHeaders(ResponseEntity.ok(Map.of("jsonrpc", "2.0", "result", Map.of())));
            case "ping" -> withMcpHeaders(ResponseEntity.ok(result(id, Map.of())));
            case "tools/list" -> withMcpHeaders(ResponseEntity.ok(toolsList(id, body)));
            case "tools/call" -> withMcpHeaders(ResponseEntity.ok(toolsCall(id, body)));
            default -> withMcpHeaders(ResponseEntity.ok(error(id, -32601, "Method not found: " + method)));
        };
    }

    /**
     * initialize 要求 params 为对象，且必须携带 protocolVersion。
     */
    private Map<String, Object> initialize(Object id, Map<String, Object> request) {
        Map<String, Object> params = requireParamsObject(request);
        if (params == null) {
            return error(id, -32602, "Invalid params: initialize requires params object");
        }
        String clientProtocolVersion = params.get("protocolVersion") == null ? null : String.valueOf(params.get("protocolVersion"));
        if (clientProtocolVersion == null || clientProtocolVersion.isBlank()) {
            return error(id, -32602, "Missing required param: params.protocolVersion");
        }

        return result(id, Map.of(
                "protocolVersion", MCP_PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "spring-mcp-probe",
                        "version", "0.1.0"
                ),
                "instructions", "Use tools/list then tools/call",
                "echoClientProtocolVersion", clientProtocolVersion
        ));
    }

    /**
     * tools/list 可以不传 params，但若传入则必须是对象。
     */
    private Map<String, Object> toolsList(Object id, Map<String, Object> request) {
        Object rawParams = request.get("params");
        if (rawParams != null && !(rawParams instanceof Map<?, ?>)) {
            return error(id, -32602, "Invalid params: tools/list params must be an object");
        }

        List<Map<String, Object>> tools = mcpToolRegistry.allTools().stream()
                .map(this::toolDescriptor)
                .toList();
        return result(id, Map.of("tools", tools));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolsCall(Object id, Map<String, Object> request) {
        Object rawParams = request.get("params");
        if (!(rawParams instanceof Map<?, ?> rawParamMap)) {
            return error(id, -32602, "Invalid params: tools/call requires params object");
        }
        Map<String, Object> params = toStringObjectMap(rawParamMap);

        String toolName = params.get("name") == null ? null : String.valueOf(params.get("name"));
        if (toolName == null || toolName.isBlank()) {
            return error(id, -32602, "Missing tool name");
        }

        Map<String, Object> arguments = Map.of();
        Object rawArguments = params.get("arguments");
        if (rawArguments != null) {
            if (!(rawArguments instanceof Map<?, ?> rawArgumentMap)) {
                return error(id, -32602, "Tool arguments must be an object");
            }
            arguments = toStringObjectMap(rawArgumentMap);
        }

        McpTool tool = mcpToolRegistry.find(toolName).orElse(null);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            Object toolResult = tool.execute(arguments);
            return toolResult(id, toolResult, false);
        } catch (IllegalArgumentException ex) {
            // 业务参数错误按工具级错误返回，避免中断 MCP 会话。
            return toolResult(id, ex.getMessage(), true);
        } catch (Exception ex) {
            // 运行期异常同样转为工具错误载荷，保持会话可继续。
            return toolResult(id, "Tool execution failed: " + ex.getMessage(), true);
        }
    }

    /**
     * MCP 工具调用的返回体约定：
     * result.content[] 固定返回文本，真实结构序列化为 JSON 字符串。
     */
    private Map<String, Object> toolResult(Object id, Object payload, boolean isError) {
        String text = toJson(payload);
        return result(id, Map.of(
                "content", List.of(
                        Map.of(
                                "type", "text",
                                "text", text
                        )
                ),
                "isError", isError
        ));
    }

    private Map<String, Object> result(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    private Map<String, Object> toolDescriptor(McpTool tool) {
        return Map.of(
                "name", tool.name(),
                "description", tool.description(),
                "inputSchema", tool.inputSchema()
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    /**
     * 当前 MCP 方法约定 JSON-RPC params 必须是对象。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requireParamsObject(Map<String, Object> request) {
        Object rawParams = request.get("params");
        if (!(rawParams instanceof Map<?, ?> rawMap)) {
            return null;
        }
        return rawMap.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(String.valueOf(entry.getKey()), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    private <T> ResponseEntity<T> withMcpHeaders(ResponseEntity<T> response) {
        // Bifrost MCP 客户端要求在 streamable HTTP 响应中返回协议版本和会话头。
        return ResponseEntity.status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Protocol-Version", MCP_PROTOCOL_VERSION)
                .header("Mcp-Session-Id", MCP_SESSION_ID)
                .body(response.getBody());
    }
}
