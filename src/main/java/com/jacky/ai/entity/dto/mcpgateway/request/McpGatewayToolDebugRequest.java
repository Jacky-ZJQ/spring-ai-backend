package com.jacky.ai.entity.dto.mcpgateway.request;

import lombok.Data;

import java.util.Map;

@Data
public class McpGatewayToolDebugRequest {

    private Map<String, Object> arguments;
}
