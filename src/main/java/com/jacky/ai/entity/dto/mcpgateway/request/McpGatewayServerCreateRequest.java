package com.jacky.ai.entity.dto.mcpgateway.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class McpGatewayServerCreateRequest {

    private String serverName;

    private String connectionType;

    private String connectionUrl;

    private String sseEndpoint;

    private String stdioCommand;

    private List<String> stdioArgs;

    private Map<String, String> stdioEnv;

    private Boolean isCodeClient;

    private Boolean isPingAvailable;

    private Integer toolSyncIntervalMinutes;

    private Map<String, String> headers;

    private String authType;

    private Boolean enabled;
}
