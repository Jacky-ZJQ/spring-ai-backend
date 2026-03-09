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
/**
 * MCP Gateway 运行时服务。
 * 负责连接管理、工具发现与调用、JSON-RPC 转发，以及不同连接类型的连通性检测。
 */
public class McpGatewayRuntimeService {

    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_DISCONNECTED = "DISCONNECTED";
    private static final String STATUS_ERROR = "ERROR";

    private final ObjectMapper objectMapper;

    /**
     * 运行时连接快照（仅内存态），用于管理端展示连接状态与更新时间。
     */
    private final Map<Long, RuntimeSnapshot> runtimeSnapshots = new ConcurrentHashMap<>();

    /**
     * 本地服务端口：用于把相对路径（如 /mcp）解析为可访问 URL。
     */
    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * 是否允许 STDIO 模式。
     */
    @Value("${app.mcp-gateway.stdio-enabled:false}")
    private boolean stdioEnabled;

    /**
     * STDIO 命令白名单（逗号分隔前缀）。
     */
    @Value("${app.mcp-gateway.stdio-command-whitelist:}")
    private String stdioCommandWhitelist;

    /**
     * 连接超时毫秒。
     */
    @Value("${app.mcp-gateway.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /**
     * 请求超时毫秒。
     */
    @Value("${app.mcp-gateway.request-timeout-ms:10000}")
    private int requestTimeoutMs;

    /**
     * 获取服务器运行时快照；若不存在则返回“未连接”默认值。
     */
    public RuntimeSnapshot getSnapshot(Long serverId) {
        return runtimeSnapshots.getOrDefault(serverId,
                new RuntimeSnapshot(false, STATUS_DISCONNECTED, "未连接", LocalDateTime.now()));
    }

    /**
     * 建立连接：
     * HTTP -> initialize
     * SSE  -> 连通性检测
     * STDIO-> 命令连通性检测
     */
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

    /**
     * 断开连接（仅更新本地快照状态，不维护长连接句柄）。
     */
    public RuntimeSnapshot disconnect(Long serverId) {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(false, STATUS_DISCONNECTED, "已断开连接", LocalDateTime.now());
        runtimeSnapshots.put(serverId, snapshot);
        return snapshot;
    }

    /**
     * Ping 检测：
     * HTTP 使用 JSON-RPC ping；
     * SSE/STDIO 走各自连通性探测。
     */
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

    /**
     * 获取远端工具列表（仅 HTTP）。
     */
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

    /**
     * 调用指定远端工具（仅 HTTP）。
     */
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

    /**
     * MCP initialize 握手。
     */
    private void initialize(McpGatewayServer server) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2025-06-18");
        params.put("clientInfo", Map.of("name", "spring-ai-portal", "version", "1.0.0"));
        params.put("capabilities", Map.of("tools", Map.of()));
        invokeJsonRpc(server, "initialize", params);
    }

    /**
     * 通用 JSON-RPC 调用器：
     * 1) 解析目标 URI
     * 2) 组装 JSON-RPC payload
     * 3) 发送 HTTP 请求并解析 result/error
     */
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

    /**
     * SSE 连通性检测：仅验证 endpoint 可访问性（不建立持续订阅）。
     */
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

    /**
     * STDIO 连通性检测：
     * 校验开关、白名单、命令参数后启动进程进行短时探活。
     */
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

    /**
     * 当前实现仅支持 HTTP 的 tools/list 与 tools/call。
     */
    private void assertHttpConnection(McpGatewayServer server) {
        if (!"HTTP".equals(normalizeType(server.getConnectionType()))) {
            throw new IllegalStateException("仅 HTTP 连接支持 tools/list 与 tools/call");
        }
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
    }

    /**
     * 将 JSON 字符串解析为 String->String map，用于 HTTP headers 或 STDIO env。
     */
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

    /**
     * 解析 STDIO args JSON（字符串数组）。
     */
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

    /**
     * 解析逗号分隔白名单前缀。
     */
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

    /**
     * 统一 URI 解析规则：
     * - http(s):// 开头：原样使用
     * - /api/*：映射到当前服务 /{去掉api前缀}
     * - /*：映射到当前服务
     * - 其他：按 host[:port][/path] 补全为 http://
     */
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

    /**
     * 解析 SSE endpoint：
     * - 未配置：回退 connectionUrl
     * - 绝对地址：直接使用
     * - 相对地址：基于 connectionUrl 拼接
     */
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

    /**
     * 安全转换 Object -> Map<String, Object>。
     */
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

    /**
     * 安全转换 Object -> List<Map<String, Object>>。
     */
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

    /**
     * 运行时状态快照。
     */
    @Data
    @AllArgsConstructor
    public static class RuntimeSnapshot {
        private boolean connected;
        private String status;
        private String message;
        private LocalDateTime updatedAt;
    }

    /**
     * MCP 工具描述。
     */
    @Data
    @AllArgsConstructor
    public static class ToolDescriptor {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }

    /**
     * MCP 工具调用结果包装。
     */
    @Data
    @AllArgsConstructor
    public static class RpcToolCallResult {
        private boolean isError;
        private Object result;
        private String rawJson;
    }
}
