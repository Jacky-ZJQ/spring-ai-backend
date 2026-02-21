package com.jacky.ai.entity.vo;

import com.jacky.ai.skills.model.SkillMode;

public class SkillVO {
    private final String code;
    private final String name;
    private final String description;
    private final SkillMode mode;
    private final String conversationPrefix;
    private final String welcomeMessage;

    public SkillVO(String code, String name, String description, SkillMode mode, String conversationPrefix, String welcomeMessage) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.mode = mode;
        this.conversationPrefix = conversationPrefix;
        this.welcomeMessage = welcomeMessage;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SkillMode getMode() {
        return mode;
    }

    public String getConversationPrefix() {
        return conversationPrefix;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }
}
