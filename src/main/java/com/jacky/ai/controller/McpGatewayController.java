package com.jacky.ai.controller;

import com.jacky.ai.mcp.service.McpGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 网关 HTTP 入口。
 * 协议细节由 McpGatewayService 统一处理，Controller 仅负责请求转发与头传递。
 */
@CrossOrigin("*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/mcp")
public class McpGatewayController {

    private final McpGatewayService mcpGatewayService;

    /**
     * 调试用途：查看当前启用的 MCP 工具定义。
     */
    @GetMapping("/tools")
    public List<Map<String, Object>> tools() {
        return mcpGatewayService.listTools();
    }

    /**
     * MCP JSON-RPC over HTTP 单请求入口。
     */
    @PostMapping
    public ResponseEntity<?> rpc(
            @RequestBody(required = false) Object body,
            @RequestHeader(value = McpGatewayService.HEADER_SESSION_ID, required = false) String sessionIdHeader,
            @RequestHeader(value = McpGatewayService.HEADER_PROTOCOL_VERSION, required = false) String protocolVersionHeader) {
        return mcpGatewayService.handleRpc(body, sessionIdHeader, protocolVersionHeader);
    }
}
