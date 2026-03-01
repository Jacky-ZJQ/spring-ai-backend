package com.jacky.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacky.ai.entity.po.McpGatewayServer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpGatewayRuntimeService {

    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_DISCONNECTED = "DISCONNECTED";
    private static final String STATUS_ERROR = "ERROR";

    private final ObjectMapper objectMapper;

    private final Map<Long, RuntimeSnapshot> runtimeSnapshots = new ConcurrentHashMap<>();

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${app.mcp-gateway.stdio-enabled:false}")
    private boolean stdioEnabled;

    @Value("${app.mcp-gateway.stdio-command-whitelist:}")
    private String stdioCommandWhitelist;

    @Value("${app.mcp-gateway.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.mcp-gateway.request-timeout-ms:10000}")
    private int requestTimeoutMs;

    public RuntimeSnapshot getSnapshot(Long serverId) {
        return runtimeSnapshots.getOrDefault(serverId,
                new RuntimeSnapshot(false, STATUS_DISCONNECTED, "未连接", LocalDateTime.now()));
    }

    public RuntimeSnapshot connect(McpGatewayServer server) {
        try {
            String type = normalizeType(server.getConnectionType());
            if ("HTTP".equals(type)) {
                initialize(server);
            } else if ("SSE".equals(type)) {
                testSseConnectivity(server);
            } else if ("STDIO".equals(type)) {
                testStdioConnectivity(server);
            } else {
                throw new IllegalArgumentException("Unsupported connection type: " + type);
            }
            RuntimeSnapshot snapshot = new RuntimeSnapshot(true, STATUS_CONNECTED, "已连接", LocalDateTime.now());
            runtimeSnapshots.put(server.getId(), snapshot);
            return snapshot;
        } catch (Exception ex) {
            RuntimeSnapshot snapshot = new RuntimeSnapshot(false, STATUS_ERROR, ex.getMessage(), LocalDateTime.now());
            runtimeSnapshots.put(server.getId(), snapshot);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public RuntimeSnapshot disconnect(Long serverId) {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(false, STATUS_DISCONNECTED, "已断开连接", LocalDateTime.now());
        runtimeSnapshots.put(serverId, snapshot);
        return snapshot;
    }

    public RuntimeSnapshot ping(McpGatewayServer server) {
        try {
            String type = normalizeType(server.getConnectionType());
            if ("HTTP".equals(type)) {
                invokeJsonRpc(server, "ping", Map.of());
            } else if ("SSE".equals(type)) {
                testSseConnectivity(server);
            } else if ("STDIO".equals(type)) {
                testStdioConnectivity(server);
            }
            RuntimeSnapshot snapshot = new RuntimeSnapshot(true, STATUS_CONNECTED, "Ping 成功", LocalDateTime.now());
            runtimeSnapshots.put(server.getId(), snapshot);
            return snapshot;
        } catch (Exception ex) {
            RuntimeSnapshot snapshot = new RuntimeSnapshot(false, STATUS_ERROR, ex.getMessage(), LocalDateTime.now());
            runtimeSnapshots.put(server.getId(), snapshot);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public List<ToolDescriptor> listTools(McpGatewayServer server) {
        assertHttpConnection(server);
        Map<String, Object> rpc = invokeJsonRpc(server, "tools/list", Map.of());
        Map<String, Object> result = asMap(rpc.get("result"));
        List<Map<String, Object>> tools = asObjectList(result.get("tools"));
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            String name = tool.get("name") == null ? "" : String.valueOf(tool.get("name"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String description = tool.get("description") == null ? "" : String.valueOf(tool.get("description"));
            Map<String, Object> inputSchema = asMap(tool.get("inputSchema"));
            descriptors.add(new ToolDescriptor(name, description, inputSchema));
        }
        return descriptors;
    }

    public RpcToolCallResult callTool(McpGatewayServer server, String toolName, Map<String, Object> arguments) {
        assertHttpConnection(server);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);

        Map<String, Object> rpc = invokeJsonRpc(server, "tools/call", params);
        Map<String, Object> result = asMap(rpc.get("result"));
        boolean isError = Boolean.TRUE.equals(result.get("isError"));

        String rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception ex) {
            rawJson = String.valueOf(result);
        }

        return new RpcToolCallResult(isError, result, rawJson);
    }

    private void initialize(McpGatewayServer server) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2025-06-18");
        params.put("clientInfo", Map.of("name", "spring-ai-portal", "version", "1.0.0"));
        params.put("capabilities", Map.of("tools", Map.of()));
        invokeJsonRpc(server, "initialize", params);
    }

    private Map<String, Object> invokeJsonRpc(McpGatewayServer server, String method, Map<String, Object> params) {
        try {
            URI uri = resolveTargetUri(server.getConnectionUrl());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jsonrpc", "2.0");
            payload.put("id", UUID.randomUUID().toString());
            payload.put("method", method);
            payload.put("params", params == null ? Map.of() : params);

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            parseHeaders(server.getHeadersJson()).forEach(builder::header);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP status=" + response.statusCode() + ", body=" + response.body());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Map<String, Object> error = asMap(parsed.get("error"));
            if (!error.isEmpty()) {
                String message = String.valueOf(error.getOrDefault("message", "JSON-RPC error"));
                throw new IllegalStateException(message);
            }
            return parsed;
        } catch (Exception ex) {
            throw new IllegalStateException("MCP 调用失败(" + method + "): " + ex.getMessage(), ex);
        }
    }

    private void testSseConnectivity(McpGatewayServer server) {
        String endpoint = resolveSseEndpoint(server);
        try {
            URI uri = resolveTargetUri(endpoint);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 500) {
                throw new IllegalStateException("SSE 连通性检查失败，status=" + status);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("SSE 连通性检查失败: " + ex.getMessage(), ex);
        }
    }

    private void testStdioConnectivity(McpGatewayServer server) {
        if (!stdioEnabled) {
            throw new IllegalStateException("STDIO 已关闭，请设置 app.mcp-gateway.stdio-enabled=true");
        }
        String command = server.getStdioCommand();
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("STDIO 模式缺少 command 配置");
        }

        List<String> whitelist = parseWhitelist();
        boolean allowed = whitelist.stream().anyMatch(prefix -> command.startsWith(prefix));
        if (!allowed) {
            throw new IllegalStateException("STDIO 命令不在白名单中: " + command);
        }

        List<String> args = parseStringListJson(server.getStdioArgsJson());
        Map<String, String> env = parseHeaders(server.getStdioEnvJson());
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);

        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);
            if (!env.isEmpty()) {
                builder.environment().putAll(env);
            }

            Process process = builder.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                process.waitFor(1, TimeUnit.SECONDS);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                return;
            }

            int exit = process.exitValue();
            if (exit != 0) {
                throw new IllegalStateException("STDIO 进程退出码=" + exit);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("STDIO 连通性检查失败: " + ex.getMessage(), ex);
        }
    }

    private void assertHttpConnection(McpGatewayServer server) {
        if (!"HTTP".equals(normalizeType(server.getConnectionType()))) {
            throw new IllegalStateException("仅 HTTP 连接支持 tools/list 与 tools/call");
        }
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
    }

    private Map<String, String> parseHeaders(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, String> headers = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() != null) {
                    headers.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return headers;
        } catch (Exception ex) {
            throw new IllegalStateException("解析 headers 失败: " + ex.getMessage(), ex);
        }
    }

    private List<String> parseStringListJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<String> values = new ArrayList<>();
            for (Object item : raw) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        } catch (Exception ex) {
            throw new IllegalStateException("解析 stdio args 失败: " + ex.getMessage(), ex);
        }
    }

    private List<String> parseWhitelist() {
        if (!StringUtils.hasText(stdioCommandWhitelist)) {
            return List.of();
        }
        List<String> prefixes = new ArrayList<>();
        for (String part : stdioCommandWhitelist.split(",")) {
            if (StringUtils.hasText(part)) {
                prefixes.add(part.trim());
            }
        }
        return prefixes;
    }

    private URI resolveTargetUri(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("连接地址不能为空");
        }
        String url = rawUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return URI.create(url);
        }
        if (url.startsWith("/api/")) {
            return URI.create("http://127.0.0.1:" + serverPort + url.substring(4));
        }
        if (url.startsWith("/")) {
            return URI.create("http://127.0.0.1:" + serverPort + url);
        }
        return URI.create("http://" + url);
    }

    private String resolveSseEndpoint(McpGatewayServer server) {
        String sseEndpoint = server.getSseEndpoint();
        if (!StringUtils.hasText(sseEndpoint)) {
            return server.getConnectionUrl();
        }
        String trimmed = sseEndpoint.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        URI base = resolveTargetUri(server.getConnectionUrl());
        if (trimmed.startsWith("/")) {
            return base.getScheme() + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "") + trimmed;
        }

        String basePath = Objects.requireNonNullElse(base.getPath(), "");
        if (!basePath.endsWith("/")) {
            int idx = basePath.lastIndexOf('/');
            basePath = idx >= 0 ? basePath.substring(0, idx + 1) : "/";
        }

        return base.getScheme() + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "") + basePath + trimmed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return converted;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asObjectList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                list.add(converted);
            }
        }
        return list;
    }

    @Data
    @AllArgsConstructor
    public static class RuntimeSnapshot {
        private boolean connected;
        private String status;
        private String message;
        private LocalDateTime updatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class ToolDescriptor {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }

    @Data
    @AllArgsConstructor
    public static class RpcToolCallResult {
        private boolean isError;
        private Object result;
        private String rawJson;
    }
}
