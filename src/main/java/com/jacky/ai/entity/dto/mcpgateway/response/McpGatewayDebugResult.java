package com.jacky.ai.entity.dto.mcpgateway.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpGatewayDebugResult {

    private String toolName;

    private boolean isError;

    private Object result;

    private String rawJson;

    private LocalDateTime time;
}
