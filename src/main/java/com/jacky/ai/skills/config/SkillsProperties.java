package com.jacky.ai.skills.config;

import com.jacky.ai.skills.model.SkillMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Skills 配置（推荐放在 application*.yaml 中）。
 *
 * 示例：
 * app:
 *   skills:
 *     enabled: true
 *     history-type: skill
 *     default-conversation-prefix: skill
 *     definitions:
 *       - code: general-assistant
 *         name: 通用助手
 *         description: 基于本地模型
 *         client-bean: ollamaChatClient
 *         mode: STREAM
 *         conversation-prefix: chat
 *         welcome-message: 你好，我是通用助手
 *         enabled: true
 */
@Data
@ConfigurationProperties(prefix = "app.skills")
public class SkillsProperties {

    /**
     * 是否启用 Skills 模块。
     */
    private boolean enabled = true;

    /**
     * 会话历史业务类型（写入 ChatHistoryRepository 时使用）。
     */
    private String historyType = "skill";

    /**
     * 全局默认 chatId 前缀，单个 skill 未配置 conversationPrefix 时使用。
     */
    private String defaultConversationPrefix = "skill";

    /**
     * Skill 列表配置。
     */
    private List<Definition> definitions = new ArrayList<>();

    @Data
    public static class Definition {
        /**
         * Skill 唯一编码
         */
        private String code;

        /**
         * 展示名称
         */
        private String name;

        /**
         * 展示说明
         */
        private String description;

        /**
         * 对应 ChatClient Bean 名称，例如：ollamaChatClient
         */
        private String clientBean;

        /**
         * 对话模式，默认 STREAM
         */
        private SkillMode mode = SkillMode.STREAM;

        /**
         * 会话前缀（可选）
         */
        private String conversationPrefix;

        /**
         * 前端默认欢迎语（可选）
         */
        private String welcomeMessage;

        /**
         * 是否启用
         */
        private boolean enabled = true;
    }
}
