package com.jacky.ai.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacky.ai.mcp.config.McpProperties;
import com.jacky.ai.mcp.exception.McpProtocolException;
import com.jacky.ai.mcp.model.McpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 网关协议服务：
 * - 统一处理 JSON-RPC 请求校验
 * - 执行 initialize / notifications/initialized / ping / tools/list / tools/call
 * - 生成带会话头的 JSON-RPC 响应
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpGatewayService {

    public static final String JSON_RPC_VERSION = "2.0";
    public static final String HEADER_SESSION_ID = "MCP-Session-Id";
    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";

    private final McpProperties mcpProperties;
    private final McpSessionManager sessionManager;
    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listTools() {
        return toolRegistry.listTools();
    }

    public ResponseEntity<?> handleRpc(Object body, String sessionIdHeader, String protocolVersionHeader) {
        if (!mcpProperties.isEnabled()) {
            return jsonRpcErrorResponse(null, -32000, "MCP gateway is disabled", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (body == null) {
            return jsonRpcErrorResponse(null, -32600, "Request body is required", HttpStatus.BAD_REQUEST);
        }
        if (body instanceof List<?>) {
            return jsonRpcErrorResponse(null, -32600, "JSON-RPC batch is not supported", HttpStatus.BAD_REQUEST);
        }
        if (!(body instanceof Map<?, ?> rawRequest)) {
            return jsonRpcErrorResponse(null, -32600, "Request must be a JSON object", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> request = castToStringObjectMap(rawRequest);
        String jsonrpc = asNullableString(request.get("jsonrpc"));
        Object id = request.get("id");
        boolean notification = !request.containsKey("id");
        if (!JSON_RPC_VERSION.equals(jsonrpc)) {
            return jsonRpcErrorResponse(id, -32600, "jsonrpc must be '2.0'", HttpStatus.BAD_REQUEST);
        }
        if (!notification && !isValidRequestId(id)) {
            return jsonRpcErrorResponse(null, -32600, "id must be string or number", HttpStatus.BAD_REQUEST);
        }

        String method = asNullableString(request.get("method"));
        if (!StringUtils.hasText(method)) {
            return jsonRpcErrorResponse(id, -32600, "method is required", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> params;
        try {
            params = asMap(request.get("params"));
        } catch (IllegalArgumentException ex) {
            return jsonRpcErrorResponse(id, -32602, ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        try {
            return dispatch(method.trim(), params, id, notification, sessionIdHeader, protocolVersionHeader);
        } catch (McpProtocolException ex) {
            return jsonRpcErrorResponse(id, ex.getCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("MCP dispatch failed. method={}, error={}", method, ex.getMessage(), ex);
            return jsonRpcErrorResponse(id, -32603, "Internal error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<?> dispatch(String method,
                                       Map<String, Object> params,
                                       Object id,
                                       boolean notification,
                                       String sessionIdHeader,
                                       String protocolVersionHeader) {
        if ("initialize".equals(method)) {
            if (notification) {
                throw new McpProtocolException(-32600, "initialize must be a request with id");
            }
            return handleInitialize(id, params, sessionIdHeader);
        }

        McpSession session = sessionManager.requireActiveSession(sessionIdHeader);
        sessionManager.validateProtocolVersion(session, protocolVersionHeader);

        if ("notifications/initialized".equals(method)) {
            sessionManager.markInitialized(session);
            HttpHeaders headers = sessionHeaders(session);
            if (notification) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).build();
            }
            return jsonRpcResultResponse(id, Map.of(), headers);
        }

        if ("ping".equals(method)) {
            HttpHeaders headers = sessionHeaders(session);
            if (notification) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).build();
            }
            return jsonRpcResultResponse(id, Map.of(), headers);
        }

        if (!session.isInitialized()) {
            throw new McpProtocolException(-32002, "Session not initialized. Send notifications/initialized first.");
        }

        return switch (method) {
            case "tools/list" -> handleToolsList(id, notification, session);
            case "tools/call" -> handleToolsCall(id, notification, session, params);
            default -> throw new McpProtocolException(-32601, "Method not found: " + method);
        };
    }

    private ResponseEntity<?> handleInitialize(Object id, Map<String, Object> params, String sessionIdHeader) {
        String clientVersion = asNullableString(params.get("protocolVersion"));
        if (!StringUtils.hasText(clientVersion)) {
            throw new McpProtocolException(-32602, "protocolVersion is required");
        }
        if (!(params.get("capabilities") instanceof Map<?, ?>)) {
            throw new McpProtocolException(-32602, "capabilities is required and must be an object");
        }
        if (!(params.get("clientInfo") instanceof Map<?, ?> clientInfoRaw)) {
            throw new McpProtocolException(-32602, "clientInfo is required and must be an object");
        }

        Map<String, Object> clientInfo = castToStringObjectMap(clientInfoRaw);
        String clientName = requiredString(clientInfo.get("name"), "clientInfo.name");
        String clientVersionName = requiredString(clientInfo.get("version"), "clientInfo.version");

        String negotiatedVersion = negotiateProtocolVersion(clientVersion.trim());
        McpSession session = sessionManager.createSession(sessionIdHeader, negotiatedVersion);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiatedVersion);
        result.put("sessionId", session.getSessionId());
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", false)
        ));
        result.put("serverInfo", Map.of(
                "name", mcpProperties.getServerName(),
                "version", mcpProperties.getServerVersion()
        ));
        result.put("instructions", "Call notifications/initialized after initialize, then use tools/list or tools/call.");
        result.put("echo", Map.of(
                "clientName", clientName,
                "clientVersion", clientVersionName
        ));

        return jsonRpcResultResponse(id, result, sessionHeaders(session));
    }

    private ResponseEntity<?> handleToolsList(Object id, boolean notification, McpSession session) {
        HttpHeaders headers = sessionHeaders(session);
        if (notification) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).build();
        }
        return jsonRpcResultResponse(id, Map.of("tools", toolRegistry.listTools()), headers);
    }

    private ResponseEntity<?> handleToolsCall(Object id, boolean notification, McpSession session, Map<String, Object> params) {
        String toolName = requiredString(params.get("name"), "name");
        Map<String, Object> arguments = asMap(params.get("arguments"));

        Object result;
        try {
            Object output = toolRegistry.callTool(toolName, arguments);
            Map<String, Object> mcpResult = new LinkedHashMap<>();
            mcpResult.put("content", List.of(Map.of("type", "text", "text", toProtocolText(output))));
            mcpResult.put("isError", false);
            if (output instanceof Map<?, ?> || output instanceof List<?>) {
                mcpResult.put("structuredContent", output);
            }
            result = mcpResult;
        } catch (McpProtocolException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new McpProtocolException(-32602, ex.getMessage());
        } catch (Exception ex) {
            log.error("MCP tool execution failed. tool={}, error={}", toolName, ex.getMessage(), ex);
            result = Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Tool execution error: " + ex.getMessage())),
                    "isError", true
            );
        }

        HttpHeaders headers = sessionHeaders(session);
        if (notification) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).build();
        }
        return jsonRpcResultResponse(id, result, headers);
    }

    private String negotiateProtocolVersion(String clientProtocolVersion) {
        List<String> supported = mcpProperties.getProtocolVersions();
        if (supported == null || supported.isEmpty()) {
            throw new IllegalStateException("app.mcp.protocol-versions must not be empty.");
        }
        return supported.contains(clientProtocolVersion) ? clientProtocolVersion : supported.get(0);
    }

    private ResponseEntity<Map<String, Object>> jsonRpcResultResponse(Object id, Object result, HttpHeaders headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", JSON_RPC_VERSION);
        body.put("id", id);
        body.put("result", result);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private ResponseEntity<Map<String, Object>> jsonRpcErrorResponse(Object id, int code, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", JSON_RPC_VERSION);
        body.put("id", id);
        body.put("error", Map.of(
                "code", code,
                "message", message == null ? "unknown error" : message
        ));
        return ResponseEntity.status(status).body(body);
    }

    private HttpHeaders sessionHeaders(McpSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HEADER_SESSION_ID, session.getSessionId());
        headers.add(HEADER_PROTOCOL_VERSION, session.getProtocolVersion());
        return headers;
    }

    private String toProtocolText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String requiredString(Object value, String fieldName) {
        String text = asNullableString(value);
        if (!StringUtils.hasText(text)) {
            throw new McpProtocolException(-32602, fieldName + " is required");
        }
        return text.trim();
    }

    private boolean isValidRequestId(Object id) {
        return id instanceof String || id instanceof Number;
    }

    private String asNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("params/arguments must be an object");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
