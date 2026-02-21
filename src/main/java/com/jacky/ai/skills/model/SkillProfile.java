package com.jacky.ai.skills.model;

/**
 * Skill 的运行态描述对象。
 *
 * @param code               唯一编码
 * @param name               展示名称
 * @param description        展示描述
 * @param clientBean         绑定的 ChatClient Bean 名称
 * @param mode               默认对话模式
 * @param conversationPrefix 会话 ID 前缀
 * @param welcomeMessage     前端可展示的欢迎语
 * @param enabled            是否启用
 */
public record SkillProfile(
        String code,
        String name,
        String description,
        String clientBean,
        SkillMode mode,
        String conversationPrefix,
        String welcomeMessage,
        boolean enabled
) {
}
