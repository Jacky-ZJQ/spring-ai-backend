package com.jacky.ai.mcp.tool.impl;

import com.jacky.ai.mcp.tool.McpToolHandler;
import com.jacky.ai.mcp.tool.support.McpArgumentHelper;
import com.jacky.ai.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * MCP 工具：skill_chat_once。
 */
@Component
@RequiredArgsConstructor
public class SkillChatOnceMcpToolHandler implements McpToolHandler {

    private final SkillService skillService;

    @Override
    public String name() {
        return "skill_chat_once";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String skillCode = McpArgumentHelper.requiredString(arguments, "skillCode");
        String prompt = McpArgumentHelper.requiredString(arguments, "prompt");
        String chatId = McpArgumentHelper.nullableString(arguments.get("chatId"));
        String finalChatId = StringUtils.hasText(chatId)
                ? chatId
                : skillService.nextChatId(skillCode);
        String content = skillService.syncChat(skillCode, prompt, finalChatId);
        return Map.of(
                "skillCode", skillCode,
                "chatId", finalChatId,
                "content", content
        );
    }
}
