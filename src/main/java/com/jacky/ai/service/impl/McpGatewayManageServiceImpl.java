package com.jacky.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerCreateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerUpdateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolBatchRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolDebugRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolPatchRequest;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayDebugResult;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayServerDetail;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayServerListItem;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayToolItem;
import com.jacky.ai.entity.po.McpGatewayServer;
import com.jacky.ai.entity.po.McpGatewayToolPolicy;
import com.jacky.ai.service.IMcpGatewayServerService;
import com.jacky.ai.service.IMcpGatewayToolPolicyService;
import com.jacky.ai.service.McpGatewayManageService;
import com.jacky.ai.service.McpGatewayRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpGatewayManageServiceImpl implements McpGatewayManageService {

    private final IMcpGatewayServerService serverService;
    private final IMcpGatewayToolPolicyService toolPolicyService;
    private final McpGatewayRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    @Override
    public List<McpGatewayServerListItem> listServers() {
        List<McpGatewayServer> servers = serverService.lambdaQuery().orderByDesc(McpGatewayServer::getId).list();
        List<McpGatewayServerListItem> result = new ArrayList<>(servers.size());
        for (McpGatewayServer server : servers) {
            List<McpGatewayToolPolicy> policies = toolPolicyService.lambdaQuery()
                    .eq(McpGatewayToolPolicy::getServerId, server.getId())
                    .list();
            long total = policies.size();
            long enabled = policies.stream().filter(p -> Boolean.TRUE.equals(p.getEnabled())).count();
            long autoExecute = policies.stream().filter(p -> Boolean.TRUE.equals(p.getAutoExecute())).count();

            McpGatewayRuntimeService.RuntimeSnapshot snapshot = runtimeService.getSnapshot(server.getId());

            McpGatewayServerListItem item = new McpGatewayServerListItem();
            item.setId(server.getId());
            item.setServerName(server.getServerName());
            item.setConnectionType(server.getConnectionType());
            item.setIsCodeClient(Boolean.TRUE.equals(server.getCodeClient()));
            item.setConnectionInfo(buildConnectionInfo(server));
            item.setEnabled(Boolean.TRUE.equals(server.getEnabled()));
            item.setStatus(snapshot.getStatus());
            item.setStatusMessage(snapshot.getMessage());
            item.setTotalToolCount((int) total);
            item.setEnabledToolCount((int) enabled);
            item.setAutoExecuteToolCount((int) autoExecute);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpGatewayServerDetail createServer(McpGatewayServerCreateRequest request) {
        validateServerRequest(request.getServerName(), request.getConnectionType(), request.getConnectionUrl(),
                request.getStdioCommand());

        McpGatewayServer server = new McpGatewayServer();
        applyRequestToServer(server, request.getServerName(), request.getConnectionType(), request.getConnectionUrl(),
                request.getSseEndpoint(), request.getStdioCommand(), request.getStdioArgs(), request.getStdioEnv(),
                request.getIsCodeClient(), request.getIsPingAvailable(), request.getToolSyncIntervalMinutes(),
                request.getHeaders(), request.getAuthType(), request.getEnabled());
        serverService.save(server);
        return getServerDetail(server.getId());
    }

    @Override
    public McpGatewayServerDetail getServerDetail(Long id) {
        McpGatewayServer server = requireServer(id);
        List<McpGatewayToolPolicy> tools = toolPolicyService.lambdaQuery()
                .eq(McpGatewayToolPolicy::getServerId, id)
                .orderByAsc(McpGatewayToolPolicy::getSortOrder)
                .orderByAsc(McpGatewayToolPolicy::getId)
                .list();

        McpGatewayRuntimeService.RuntimeSnapshot snapshot = runtimeService.getSnapshot(id);

        McpGatewayServerDetail detail = new McpGatewayServerDetail();
        detail.setId(server.getId());
        detail.setServerName(server.getServerName());
        detail.setConnectionType(server.getConnectionType());
        detail.setConnectionUrl(server.getConnectionUrl());
        detail.setSseEndpoint(server.getSseEndpoint());
        detail.setStdioCommand(server.getStdioCommand());
        detail.setStdioArgs(parseStringList(server.getStdioArgsJson()));
        detail.setStdioEnv(parseStringMap(server.getStdioEnvJson()));
        detail.setIsCodeClient(Boolean.TRUE.equals(server.getCodeClient()));
        detail.setIsPingAvailable(Boolean.TRUE.equals(server.getPingAvailable()));
        detail.setToolSyncIntervalMinutes(server.getToolSyncIntervalMinutes());
        detail.setHeaders(parseStringMap(server.getHeadersJson()));
        detail.setAuthType(server.getAuthType());
        detail.setEnabled(Boolean.TRUE.equals(server.getEnabled()));
        detail.setStatus(snapshot.getStatus());
        detail.setStatusMessage(snapshot.getMessage());
        detail.setCreatedAt(server.getCreatedAt());
        detail.setUpdatedAt(server.getUpdatedAt());
        detail.setTools(tools.stream().map(this::toToolItem).toList());
        detail.setConfigSnapshot(buildConfigSnapshot(detail));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpGatewayServerDetail updateServer(Long id, McpGatewayServerUpdateRequest request) {
        McpGatewayServer server = requireServer(id);
        validateServerRequest(request.getServerName(), request.getConnectionType(), request.getConnectionUrl(),
                request.getStdioCommand());

        applyRequestToServer(server, request.getServerName(), request.getConnectionType(), request.getConnectionUrl(),
                request.getSseEndpoint(), request.getStdioCommand(), request.getStdioArgs(), request.getStdioEnv(),
                request.getIsCodeClient(), request.getIsPingAvailable(), request.getToolSyncIntervalMinutes(),
                request.getHeaders(), request.getAuthType(), request.getEnabled());
        serverService.updateById(server);
        return getServerDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(Long id) {
        requireServer(id);
        toolPolicyService.lambdaUpdate().eq(McpGatewayToolPolicy::getServerId, id).remove();
        serverService.removeById(id);
        runtimeService.disconnect(id);
    }

    @Override
    public McpGatewayServerDetail connectServer(Long id) {
        McpGatewayServer server = requireServer(id);
        if (!Boolean.TRUE.equals(server.getEnabled())) {
            throw new IllegalStateException("请先启用服务器配置后再连接");
        }
        runtimeService.connect(server);
        return getServerDetail(id);
    }

    @Override
    public McpGatewayServerDetail disconnectServer(Long id) {
        requireServer(id);
        runtimeService.disconnect(id);
        return getServerDetail(id);
    }

    @Override
    public McpGatewayServerDetail pingServer(Long id) {
        McpGatewayServer server = requireServer(id);
        if (!Boolean.TRUE.equals(server.getPingAvailable())) {
            throw new IllegalStateException("当前服务器未启用 Ping 检查");
        }
        runtimeService.ping(server);
        return getServerDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<McpGatewayToolItem> syncTools(Long id) {
        McpGatewayServer server = requireServer(id);
        if (!"HTTP".equalsIgnoreCase(server.getConnectionType())) {
            throw new IllegalStateException("仅 HTTP 连接类型支持 tools/sync");
        }

        List<McpGatewayRuntimeService.ToolDescriptor> descriptors = runtimeService.listTools(server);
        List<McpGatewayToolPolicy> existing = toolPolicyService.lambdaQuery()
                .eq(McpGatewayToolPolicy::getServerId, id)
                .list();
        Map<String, McpGatewayToolPolicy> existingMap = existing.stream()
                .collect(Collectors.toMap(McpGatewayToolPolicy::getToolName, item -> item, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        int sortOrder = 1;
        Set<String> incomingNames = descriptors.stream().map(McpGatewayRuntimeService.ToolDescriptor::getName)
                .collect(Collectors.toSet());

        for (McpGatewayRuntimeService.ToolDescriptor descriptor : descriptors) {
            McpGatewayToolPolicy policy = existingMap.get(descriptor.getName());
            if (policy == null) {
                policy = new McpGatewayToolPolicy();
                policy.setServerId(id);
                policy.setToolName(descriptor.getName());
                policy.setEnabled(true);
                policy.setAutoExecute(false);
                policy.setCostUsd(BigDecimal.ZERO);
            }
            policy.setToolDescription(descriptor.getDescription());
            policy.setInputSchemaJson(toJson(descriptor.getInputSchema()));
            policy.setSortOrder(sortOrder++);
            policy.setLastSyncAt(now);

            if (policy.getId() == null) {
                toolPolicyService.save(policy);
            } else {
                toolPolicyService.updateById(policy);
            }
        }

        List<Long> staleIds = existing.stream()
                .filter(item -> !incomingNames.contains(item.getToolName()))
                .map(McpGatewayToolPolicy::getId)
                .toList();
        if (!staleIds.isEmpty()) {
            toolPolicyService.removeByIds(staleIds);
        }

        return toolPolicyService.lambdaQuery()
                .eq(McpGatewayToolPolicy::getServerId, id)
                .orderByAsc(McpGatewayToolPolicy::getSortOrder)
                .orderByAsc(McpGatewayToolPolicy::getId)
                .list()
                .stream()
                .map(this::toToolItem)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpGatewayToolItem patchTool(Long id, String toolName, McpGatewayToolPatchRequest request) {
        requireServer(id);
        McpGatewayToolPolicy policy = requireToolPolicy(id, toolName);
        if (request.getEnabled() != null) {
            policy.setEnabled(request.getEnabled());
        }
        if (request.getAutoExecute() != null) {
            policy.setAutoExecute(request.getAutoExecute());
        }
        if (request.getCostUsd() != null) {
            policy.setCostUsd(request.getCostUsd());
        }
        toolPolicyService.updateById(policy);
        return toToolItem(policy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<McpGatewayToolItem> batchTools(Long id, McpGatewayToolBatchRequest request) {
        requireServer(id);
        if (request == null || !StringUtils.hasText(request.getAction())) {
            throw new IllegalArgumentException("批量动作不能为空");
        }

        String action = request.getAction().trim();
        if ("enableAll".equals(action)) {
            toolPolicyService.lambdaUpdate()
                    .eq(McpGatewayToolPolicy::getServerId, id)
                    .set(McpGatewayToolPolicy::getEnabled, true)
                    .update();
        } else if ("disableAutoExecute".equals(action)) {
            toolPolicyService.lambdaUpdate()
                    .eq(McpGatewayToolPolicy::getServerId, id)
                    .set(McpGatewayToolPolicy::getAutoExecute, false)
                    .update();
        } else {
            throw new IllegalArgumentException("不支持的批量动作: " + action);
        }

        return toolPolicyService.lambdaQuery()
                .eq(McpGatewayToolPolicy::getServerId, id)
                .orderByAsc(McpGatewayToolPolicy::getSortOrder)
                .orderByAsc(McpGatewayToolPolicy::getId)
                .list()
                .stream()
                .map(this::toToolItem)
                .toList();
    }

    @Override
    public McpGatewayDebugResult debugTool(Long id, String toolName, McpGatewayToolDebugRequest request) {
        McpGatewayServer server = requireServer(id);
        if (!"HTTP".equalsIgnoreCase(server.getConnectionType())) {
            throw new IllegalStateException("当前连接类型仅支持管理与连通测试，不支持 tools/call");
        }
        McpGatewayRuntimeService.RuntimeSnapshot snapshot = runtimeService.getSnapshot(id);
        if (!snapshot.isConnected()) {
            throw new IllegalStateException("请先连接服务器，再执行在线调试");
        }

        McpGatewayToolPolicy policy = requireToolPolicy(id, toolName);
        if (!Boolean.TRUE.equals(policy.getEnabled())) {
            throw new IllegalStateException("工具未启用，无法调试调用");
        }

        Map<String, Object> arguments = request == null || request.getArguments() == null
                ? Map.of()
                : request.getArguments();

        McpGatewayRuntimeService.RpcToolCallResult rpcResult = runtimeService.callTool(server, toolName, arguments);
        McpGatewayDebugResult result = new McpGatewayDebugResult();
        result.setToolName(toolName);
        result.setError(rpcResult.isError());
        result.setResult(rpcResult.getResult());
        result.setRawJson(rpcResult.getRawJson());
        result.setTime(LocalDateTime.now());
        return result;
    }

    private McpGatewayToolPolicy requireToolPolicy(Long serverId, String toolName) {
        McpGatewayToolPolicy policy = toolPolicyService.lambdaQuery()
                .eq(McpGatewayToolPolicy::getServerId, serverId)
                .eq(McpGatewayToolPolicy::getToolName, toolName)
                .last("limit 1")
                .one();
        if (policy == null) {
            throw new IllegalArgumentException("工具不存在: " + toolName);
        }
        return policy;
    }

    private McpGatewayServer requireServer(Long id) {
        McpGatewayServer server = serverService.getById(id);
        if (server == null) {
            throw new IllegalArgumentException("服务器不存在: id=" + id);
        }
        return server;
    }

    private void applyRequestToServer(McpGatewayServer server,
                                      String serverName,
                                      String connectionType,
                                      String connectionUrl,
                                      String sseEndpoint,
                                      String stdioCommand,
                                      List<String> stdioArgs,
                                      Map<String, String> stdioEnv,
                                      Boolean isCodeClient,
                                      Boolean isPingAvailable,
                                      Integer toolSyncIntervalMinutes,
                                      Map<String, String> headers,
                                      String authType,
                                      Boolean enabled) {
        server.setServerName(serverName.trim());
        server.setConnectionType(connectionType.trim().toUpperCase());
        server.setConnectionUrl(StringUtils.hasText(connectionUrl) ? connectionUrl.trim() : null);
        server.setSseEndpoint(StringUtils.hasText(sseEndpoint) ? sseEndpoint.trim() : null);
        server.setStdioCommand(StringUtils.hasText(stdioCommand) ? stdioCommand.trim() : null);
        server.setStdioArgsJson(toJson(stdioArgs == null ? List.of() : stdioArgs));
        server.setStdioEnvJson(toJson(stdioEnv == null ? Map.of() : stdioEnv));
        server.setCodeClient(Boolean.TRUE.equals(isCodeClient));
        server.setPingAvailable(Boolean.TRUE.equals(isPingAvailable));
        server.setToolSyncIntervalMinutes(toolSyncIntervalMinutes == null ? 10 : Math.max(1, toolSyncIntervalMinutes));
        server.setHeadersJson(toJson(headers == null ? Map.of() : headers));
        server.setAuthType(StringUtils.hasText(authType) ? authType.trim() : "none");
        server.setEnabled(enabled == null || enabled);
    }

    private void validateServerRequest(String serverName, String connectionType, String connectionUrl, String stdioCommand) {
        if (!StringUtils.hasText(serverName)) {
            throw new IllegalArgumentException("serverName 不能为空");
        }
        if (!StringUtils.hasText(connectionType)) {
            throw new IllegalArgumentException("connectionType 不能为空");
        }

        String normalizedType = connectionType.trim().toUpperCase();
        if (!Set.of("HTTP", "SSE", "STDIO").contains(normalizedType)) {
            throw new IllegalArgumentException("connectionType 仅支持 HTTP/SSE/STDIO");
        }

        if (Set.of("HTTP", "SSE").contains(normalizedType) && !StringUtils.hasText(connectionUrl)) {
            throw new IllegalArgumentException("HTTP/SSE 模式必须填写 connectionUrl");
        }
        if ("STDIO".equals(normalizedType) && !StringUtils.hasText(stdioCommand)) {
            throw new IllegalArgumentException("STDIO 模式必须填写 stdioCommand");
        }
    }

    private McpGatewayToolItem toToolItem(McpGatewayToolPolicy policy) {
        McpGatewayToolItem item = new McpGatewayToolItem();
        item.setToolName(policy.getToolName());
        item.setToolDescription(policy.getToolDescription());
        item.setInputSchema(parseObjectMap(policy.getInputSchemaJson()));
        item.setEnabled(Boolean.TRUE.equals(policy.getEnabled()));
        item.setAutoExecute(Boolean.TRUE.equals(policy.getAutoExecute()));
        item.setCostUsd(policy.getCostUsd() == null ? BigDecimal.ZERO : policy.getCostUsd());
        item.setSortOrder(policy.getSortOrder());
        item.setLastSyncAt(policy.getLastSyncAt());
        return item;
    }

    private String buildConnectionInfo(McpGatewayServer server) {
        if ("STDIO".equalsIgnoreCase(server.getConnectionType())) {
            return StringUtils.hasText(server.getStdioCommand()) ? server.getStdioCommand() : "-";
        }
        return StringUtils.hasText(server.getConnectionUrl()) ? server.getConnectionUrl() : "-";
    }

    private Map<String, Object> buildConfigSnapshot(McpGatewayServerDetail detail) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("is_code_client", detail.getIsCodeClient());
        snapshot.put("connection_type", detail.getConnectionType());

        Map<String, Object> connectionString = new LinkedHashMap<>();
        connectionString.put("value", detail.getConnectionUrl());
        connectionString.put("sse_endpoint", detail.getSseEndpoint());
        connectionString.put("env", detail.getStdioEnv());
        snapshot.put("connection_string", connectionString);

        snapshot.put("auth_type", detail.getAuthType());
        snapshot.put("is_ping_available", detail.getIsPingAvailable());
        snapshot.put("tool_sync_interval", detail.getToolSyncIntervalMinutes() == null
                ? null
                : detail.getToolSyncIntervalMinutes() * 60_000L);
        return snapshot;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, String> parseStringMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("Parse string map failed, json={}", json, ex);
            return Map.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                if (value != null && StringUtils.hasText(String.valueOf(value))) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("Parse string list failed, json={}", json, ex);
            return List.of();
        }
    }

    private Map<String, Object> parseObjectMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Parse object map failed, json={}", json, ex);
            return Map.of();
        }
    }
}
