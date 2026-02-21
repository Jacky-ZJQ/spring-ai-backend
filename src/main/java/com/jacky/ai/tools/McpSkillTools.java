package com.jacky.ai.tools;

import com.jacky.ai.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * MCP 专用技能工具：提供一次同步技能问答能力。
 */
@Component
@RequiredArgsConstructor
public class McpSkillTools {

    private final SkillService skillService;

    @Tool(name = "skill_chat_once", description = "通过技能中心发起一次同步问答")
    public Map<String, Object> skillChatOnce(
            @ToolParam(description = "技能编码") String skillCode,
            @ToolParam(description = "用户问题") String prompt,
            @ToolParam(required = false, description = "可选会话ID，不传则自动生成") String chatId) {

        String finalChatId = StringUtils.hasText(chatId)
                ? chatId.trim()
                : skillService.nextChatId(skillCode);
        String content = skillService.syncChat(skillCode, prompt, finalChatId);
        return Map.of(
                "skillCode", skillCode,
                "chatId", finalChatId,
                "content", content
        );
    }
}
