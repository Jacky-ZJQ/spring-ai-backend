package com.jacky.ai.skills;

import com.jacky.ai.skills.exception.SkillException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * ChatClient Bean 解析器。
 */
@Component
@RequiredArgsConstructor
public class ChatClientResolver {

    private final ApplicationContext applicationContext;

    public ChatClient resolveByBeanName(String beanName) {
        if (!applicationContext.containsBean(beanName)) {
            throw new SkillException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SKILL_CLIENT_NOT_FOUND",
                    "ChatClient bean not found: " + beanName
            );
        }
        return applicationContext.getBean(beanName, ChatClient.class);
    }
}
