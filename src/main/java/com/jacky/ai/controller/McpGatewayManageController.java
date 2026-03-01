package com.jacky.ai.controller;

import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerCreateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayServerUpdateRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolBatchRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolDebugRequest;
import com.jacky.ai.entity.dto.mcpgateway.request.McpGatewayToolPatchRequest;
import com.jacky.ai.service.McpGatewayManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/mcp-gateway")
public class McpGatewayManageController {

    private final McpGatewayManageService manageService;

    @GetMapping("/servers")
    public Map<String, Object> listServers() {
        return success(manageService.listServers(), "查询成功");
    }

    @PostMapping("/servers")
    public Map<String, Object> createServer(@RequestBody McpGatewayServerCreateRequest request) {
        return success(manageService.createServer(request), "创建成功");
    }

    @GetMapping("/servers/{id}")
    public Map<String, Object> getServer(@PathVariable("id") Long id) {
        return success(manageService.getServerDetail(id), "查询成功");
    }

    @PutMapping("/servers/{id}")
    public Map<String, Object> updateServer(@PathVariable("id") Long id,
                                            @RequestBody McpGatewayServerUpdateRequest request) {
        return success(manageService.updateServer(id, request), "更新成功");
    }

    @DeleteMapping("/servers/{id}")
    public Map<String, Object> deleteServer(@PathVariable("id") Long id) {
        manageService.deleteServer(id);
        return success(null, "删除成功");
    }

    @PostMapping("/servers/{id}/connect")
    public Map<String, Object> connect(@PathVariable("id") Long id) {
        return success(manageService.connectServer(id), "连接成功");
    }

    @PostMapping("/servers/{id}/disconnect")
    public Map<String, Object> disconnect(@PathVariable("id") Long id) {
        return success(manageService.disconnectServer(id), "断开成功");
    }

    @PostMapping("/servers/{id}/ping")
    public Map<String, Object> ping(@PathVariable("id") Long id) {
        return success(manageService.pingServer(id), "Ping 成功");
    }

    @PostMapping("/servers/{id}/tools/sync")
    public Map<String, Object> syncTools(@PathVariable("id") Long id) {
        return success(manageService.syncTools(id), "工具同步成功");
    }

    @PatchMapping("/servers/{id}/tools/{toolName}")
    public Map<String, Object> patchTool(@PathVariable("id") Long id,
                                         @PathVariable("toolName") String toolName,
                                         @RequestBody McpGatewayToolPatchRequest request) {
        return success(manageService.patchTool(id, toolName, request), "工具更新成功");
    }

    @PostMapping("/servers/{id}/tools/batch")
    public Map<String, Object> batchTools(@PathVariable("id") Long id,
                                          @RequestBody McpGatewayToolBatchRequest request) {
        return success(manageService.batchTools(id, request), "批量操作成功");
    }

    @PostMapping("/servers/{id}/tools/{toolName}/debug")
    public Map<String, Object> debugTool(@PathVariable("id") Long id,
                                         @PathVariable("toolName") String toolName,
                                         @RequestBody(required = false) McpGatewayToolDebugRequest request) {
        return success(manageService.debugTool(id, toolName, request), "调试执行完成");
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> onException(Exception ex) {
        return fail(ex.getMessage());
    }

    private Map<String, Object> success(Object data, String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", 1);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", 0);
        result.put("msg", msg == null ? "操作失败" : msg);
        result.put("data", null);
        return result;
    }
}
