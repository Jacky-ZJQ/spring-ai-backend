package com.jacky.ai.skills;

import com.jacky.ai.skills.config.SkillsProperties;
import com.jacky.ai.skills.exception.SkillException;
import com.jacky.ai.skills.model.SkillMode;
import com.jacky.ai.skills.model.SkillProfile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 从配置文件加载并维护 Skill 注册表。
 */
@Component
@RequiredArgsConstructor
public class SkillRegistry {

    private final SkillsProperties skillsProperties;

    /**
     * key 使用 lower-case skill code，便于无感匹配。
     */
    private final Map<String, SkillProfile> profiles = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        if (!skillsProperties.isEnabled()) {
            return;
        }
        if (skillsProperties.getDefinitions() == null || skillsProperties.getDefinitions().isEmpty()) {
            throw new SkillException(HttpStatus.INTERNAL_SERVER_ERROR, "SKILL_CONFIG_EMPTY", "No skill definitions configured.");
        }

        for (SkillsProperties.Definition definition : skillsProperties.getDefinitions()) {
            validateDefinition(definition);
            String normalizedCode = definition.getCode().trim().toLowerCase();
            if (profiles.containsKey(normalizedCode)) {
                throw new SkillException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "SKILL_CONFIG_DUPLICATE",
                        "Duplicate skill code found: " + definition.getCode()
                );
            }

            SkillProfile profile = new SkillProfile(
                    definition.getCode().trim(),
                    definition.getName().trim(),
                    definition.getDescription().trim(),
                    definition.getClientBean().trim(),
                    definition.getMode() == null ? SkillMode.STREAM : definition.getMode(),
                    resolveConversationPrefix(definition),
                    StringUtils.hasText(definition.getWelcomeMessage()) ? definition.getWelcomeMessage().trim() : "",
                    definition.isEnabled()
            );
            profiles.put(normalizedCode, profile);
        }

        long enabledCount = profiles.values().stream().filter(SkillProfile::enabled).count();
        if (enabledCount == 0) {
            throw new SkillException(HttpStatus.INTERNAL_SERVER_ERROR, "SKILL_CONFIG_EMPTY", "All skills are disabled.");
        }
    }

    public List<SkillProfile> listEnabled() {
        return profiles.values().stream().filter(SkillProfile::enabled).collect(Collectors.toList());
    }

    public SkillProfile getRequiredEnabled(String skillCode) {
        if (!skillsProperties.isEnabled()) {
            throw new SkillException(HttpStatus.SERVICE_UNAVAILABLE, "SKILL_DISABLED", "Skills module is disabled.");
        }
        if (!StringUtils.hasText(skillCode)) {
            throw new SkillException(HttpStatus.BAD_REQUEST, "SKILL_CODE_EMPTY", "Skill code is required.");
        }

        SkillProfile profile = profiles.get(skillCode.trim().toLowerCase());
        if (profile == null || !profile.enabled()) {
            throw new SkillException(HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Skill not found: " + skillCode);
        }
        return profile;
    }

    public String getHistoryType() {
        return StringUtils.hasText(skillsProperties.getHistoryType())
                ? skillsProperties.getHistoryType().trim()
                : "skill";
    }

    public String getDefaultConversationPrefix() {
        return StringUtils.hasText(skillsProperties.getDefaultConversationPrefix())
                ? skillsProperties.getDefaultConversationPrefix().trim()
                : "skill";
    }

    private String resolveConversationPrefix(SkillsProperties.Definition definition) {
        if (StringUtils.hasText(definition.getConversationPrefix())) {
            return definition.getConversationPrefix().trim();
        }
        return getDefaultConversationPrefix();
    }

    private void validateDefinition(SkillsProperties.Definition definition) {
        if (!StringUtils.hasText(definition.getCode())) {
            throw new SkillException(HttpStatus.INTERNAL_SERVER_ERROR, "SKILL_CONFIG_INVALID", "Skill code must not be empty.");
        }
        if (!StringUtils.hasText(definition.getName())) {
            throw new SkillException(HttpStatus.INTERNAL_SERVER_ERROR, "SKILL_CONFIG_INVALID", "Skill name must not be empty.");
        }
        if (!StringUtils.hasText(definition.getDescription())) {
            throw new SkillException(HttpStatus.INTERNAL_SERVER_ERROR, "SKILL_CONFIG_INVALID", "Skill description must not be empty.");
        }
        if (!StringUtils.hasText(definition.getClientBean())) {
            throw new SkillException(HttpStatus.INTERNAL_SERVER_ERROR, "SKILL_CONFIG_INVALID", "Skill clientBean must not be empty.");
        }
    }
}
