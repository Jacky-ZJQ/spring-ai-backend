package com.jacky.ai.entity.dto.mcpgateway.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class McpGatewayToolItem {

    private String toolName;

    private String toolDescription;

    private Map<String, Object> inputSchema;

    private Boolean enabled;

    private Boolean autoExecute;

    private BigDecimal costUsd;

    private Integer sortOrder;

    private LocalDateTime lastSyncAt;
}
