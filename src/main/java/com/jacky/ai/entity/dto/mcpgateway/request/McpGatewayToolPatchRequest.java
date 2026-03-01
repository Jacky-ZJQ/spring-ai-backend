package com.jacky.ai.entity.dto.mcpgateway.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class McpGatewayToolPatchRequest {

    private Boolean enabled;

    private Boolean autoExecute;

    private BigDecimal costUsd;
}
