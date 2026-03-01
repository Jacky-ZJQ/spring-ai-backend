package com.jacky.ai.mcp;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 工具注册中心。
 */
@Component
public class McpToolRegistry {

    private final Map<String, McpTool> toolsByName;

    public McpToolRegistry(List<McpTool> tools) {
        Map<String, McpTool> map = new LinkedHashMap<>();
        for (McpTool tool : tools) {
            // 工具名是外部契约，重名必须在启动期快速失败，避免运行期歧义。
            if (map.containsKey(tool.name())) {
                throw new IllegalStateException("Duplicate MCP tool name: " + tool.name());
            }
            map.put(tool.name(), tool);
        }
        this.toolsByName = Map.copyOf(map);
    }

    public List<McpTool> allTools() {
        return toolsByName.values().stream().toList();
    }

    public Optional<McpTool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }
}
