package com.jacky.ai.service;

import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerCreateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerUpdateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolBatchRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolDebugRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolPatchRequest;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayDebugResult;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayServerDetail;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayServerListItem;
import com.jacky.ai.entity.dto.mcpgateway.response.McpGatewayToolItem;

import java.util.List;

public interface McpGatewayManageService {

    List<McpGatewayServerListItem> listServers();

    McpGatewayServerDetail createServer(McpGatewayServerCreateRequest request);

    McpGatewayServerDetail getServerDetail(Long id);

    McpGatewayServerDetail updateServer(Long id, McpGatewayServerUpdateRequest request);

    void deleteServer(Long id);

    McpGatewayServerDetail connectServer(Long id);

    McpGatewayServerDetail disconnectServer(Long id);

    McpGatewayServerDetail pingServer(Long id);

    List<McpGatewayToolItem> syncTools(Long id);

    McpGatewayToolItem patchTool(Long id, String toolName, McpGatewayToolPatchRequest request);

    List<McpGatewayToolItem> batchTools(Long id, McpGatewayToolBatchRequest request);

    McpGatewayDebugResult debugTool(Long id, String toolName, McpGatewayToolDebugRequest request);
}
