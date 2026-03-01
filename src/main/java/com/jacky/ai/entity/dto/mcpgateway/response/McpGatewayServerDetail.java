package com.jacky.ai.entity.dto.mcpgateway.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class McpGatewayServerDetail {

    private Long id;

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

    private String status;

    private String statusMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<McpGatewayToolItem> tools;

    private Map<String, Object> configSnapshot;
}
