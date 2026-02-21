package com.jacky.ai.entity.query;

import lombok.Data;

/**
 * Skills 聊天请求体。
 */
@Data
public class SkillChatRequest {
    /**
     * 用户输入内容
     */
    private String prompt;

    /**
     * 可选会话ID；为空时后端自动生成
     */
    private String chatId;
}
