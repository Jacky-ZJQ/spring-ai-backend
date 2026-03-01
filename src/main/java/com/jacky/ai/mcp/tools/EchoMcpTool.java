package com.jacky.ai.mcp.tools;

import com.jacky.ai.mcp.McpArgUtils;
import com.jacky.ai.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EchoMcpTool implements McpTool {

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "回显输入文本";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "text to echo"
                        )
                ),
                "required", java.util.List.of("text")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        // 保留最小回显工具，便于协议联通性排查。
        String text = McpArgUtils.requiredString(arguments, "text");
        return Map.of("text", text);
    }
}
