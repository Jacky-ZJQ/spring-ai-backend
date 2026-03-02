package com.jacky.ai.controller;

import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerCreateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerUpdateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolBatchRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolDebugRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolPatchRequest;
import com.jacky.ai.service.McpGatewayManageService;
import com.jacky.ai.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/mcp-gateway")
public class McpGatewayManageController {

    private final McpGatewayManageService manageService;

    /**
     * 查询所有MCP网关服务器
     * @return 所有MCP网关服务器的列表
     */
    @GetMapping("/servers")
    public Map<String, Object> listServers() {
        return ResponseUtil.success(manageService.listServers(), "查询成功");
    }

    /**
     * 创建一个新的MCP网关服务器
     * @param request 包含新MCP网关服务器配置的请求体
     * @return 创建成功后的MCP网关服务器信息
     */
    @PostMapping("/servers")
    public Map<String, Object> createServer(@RequestBody McpGatewayServerCreateRequest request) {
        return ResponseUtil.success(manageService.createServer(request), "创建成功");
    }

    /**
     * 查询指定ID的MCP网关服务器详情
     * @param id MCP网关服务器的唯一标识符
     * @return 指定ID的MCP网关服务器详情
     */
    @GetMapping("/servers/{id}")
    public Map<String, Object> getServer(@PathVariable("id") Long id) {
        return ResponseUtil.success(manageService.getServerDetail(id), "查询成功");
    }

    /**
     * 更新指定ID的MCP网关服务器
     * @param id MCP网关服务器的唯一标识符
     * @param request 包含更新内容的请求体
     * @return 更新后的MCP网关服务器信息
     */
    @PutMapping("/servers/{id}")
    public Map<String, Object> updateServer(@PathVariable("id") Long id,
                                            @RequestBody McpGatewayServerUpdateRequest request) {
        return ResponseUtil.success(manageService.updateServer(id, request), "更新成功");
    }

    /**
     * 删除指定ID的MCP网关服务器
     * @param id MCP网关服务器的唯一标识符
     * @return 删除操作结果
     */
    @DeleteMapping("/servers/{id}")
    public Map<String, Object> deleteServer(@PathVariable("id") Long id) {
        manageService.deleteServer(id);
        return ResponseUtil.success(null, "删除成功");
    }

    /**
     * 连接指定ID的MCP网关服务器
     * @param id MCP网关服务器的唯一标识符
     * @return 连接操作结果
     */
    @PostMapping("/servers/{id}/connect")
    public Map<String, Object> connect(@PathVariable("id") Long id) {
        return ResponseUtil.success(manageService.connectServer(id), "连接成功");
    }

    /**
     * 断开指定ID的MCP网关服务器连接
     * @param id MCP网关服务器的唯一标识符
     * @return 断开操作结果
     */
    @PostMapping("/servers/{id}/disconnect")
    public Map<String, Object> disconnect(@PathVariable("id") Long id) {
        return ResponseUtil.success(manageService.disconnectServer(id), "断开成功");
    }

    /**
     * 对指定ID的MCP网关服务器执行Ping操作，检测服务器可用性
     * @param id MCP网关服务器的唯一标识符
     * @return Ping操作结果
     */
    @PostMapping("/servers/{id}/ping")
    public Map<String, Object> ping(@PathVariable("id") Long id) {
        return ResponseUtil.success(manageService.pingServer(id), "Ping 成功");
    }

    /**
     * 同步指定ID的MCP网关服务器的工具列表
     * @param id MCP网关服务器的唯一标识符
     * @return 同步操作结果
     */
    @PostMapping("/servers/{id}/tools/sync")
    public Map<String, Object> syncTools(@PathVariable("id") Long id) {
        return ResponseUtil.success(manageService.syncTools(id), "工具同步成功");
    }

    /**
     * 更新指定MCP网关服务器的工具配置
     * @param id MCP网关服务器的唯一标识符
     * @param toolName 工具名称
     * @param request 包含工具更新内容的请求体
     * @return 更新操作结果
     */
    @PatchMapping("/servers/{id}/tools/{toolName}")
    public Map<String, Object> patchTool(@PathVariable("id") Long id,
                                         @PathVariable("toolName") String toolName,
                                         @RequestBody McpGatewayToolPatchRequest request) {
        return ResponseUtil.success(manageService.patchTool(id, toolName, request), "工具更新成功");
    }

    /**
     * 对指定MCP网关服务器的工具执行批量操作
     * @param id MCP网关服务器的唯一标识符
     * @param request 包含批量操作内容的请求体
     * @return 批量操作结果
     */
    @PostMapping("/servers/{id}/tools/batch")
    public Map<String, Object> batchTools(@PathVariable("id") Long id,
                                          @RequestBody McpGatewayToolBatchRequest request) {
        return ResponseUtil.success(manageService.batchTools(id, request), "批量操作成功");
    }

    /**
     * 调试执行指定MCP网关服务器的工具
     * @param id MCP网关服务器的唯一标识符
     * @param toolName 工具名称
     * @param request 包含调试参数的请求体（可选）
     * @return 调试执行结果
     */
    @PostMapping("/servers/{id}/tools/{toolName}/debug")
    public Map<String, Object> debugTool(@PathVariable("id") Long id,
                                         @PathVariable("toolName") String toolName,
                                         @RequestBody(required = false) McpGatewayToolDebugRequest request) {
        return ResponseUtil.success(manageService.debugTool(id, toolName, request), "调试执行完成");
    }

}