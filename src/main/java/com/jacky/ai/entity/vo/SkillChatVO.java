package com.jacky.ai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Skills 同步聊天响应体。
 */
@Data
@AllArgsConstructor
public class SkillChatVO {
    private String skillCode;
    private String chatId;
    private String content;
}
