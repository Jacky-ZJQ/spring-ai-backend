package com.jacky.ai.entity.dto.mcpgateway.response;

import lombok.Data;

@Data
public class McpGatewayServerListItem {

    private Long id;

    private String serverName;

    private String connectionType;

    private Boolean isCodeClient;

    private String connectionInfo;

    private Boolean enabled;

    private String status;

    private String statusMessage;

    private Integer totalToolCount;

    private Integer enabledToolCount;

    private Integer autoExecuteToolCount;
}
