package com.jacky.ai.service.impl;

import com.jacky.ai.entity.vo.SkillVO;
import com.jacky.ai.repository.ChatHistoryRepository;
import com.jacky.ai.service.SkillService;
import com.jacky.ai.skills.ChatClientResolver;
import com.jacky.ai.skills.SkillRegistry;
import com.jacky.ai.skills.exception.SkillException;
import com.jacky.ai.skills.model.SkillMode;
import com.jacky.ai.skills.model.SkillProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * Skills 业务服务实现：
 * - 通过 SkillRegistry 读取配置
 * - 通过 ChatClientResolver 动态绑定 ChatClient
 * - 统一处理 chatId 和会话历史记录
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillServiceImpl implements SkillService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final SkillRegistry skillRegistry;
    private final ChatClientResolver chatClientResolver;

    @Override
    public List<SkillVO> listSkills() {
        return skillRegistry.listEnabled()
                .stream()
                .map(this::toSkillVO)
                .toList();
    }

    @Override
    public SkillVO getSkill(String skillCode) {
        SkillProfile profile = skillRegistry.getRequiredEnabled(skillCode);
        return toSkillVO(profile);
    }

    @Override
    public Flux<String> streamChat(String skillCode, String prompt, String chatId) {
        validatePrompt(prompt);

        SkillProfile profile = skillRegistry.getRequiredEnabled(skillCode);
        String finalChatId = StringUtils.hasText(chatId) ? chatId.trim() : nextChatId(skillCode);
        chatHistoryRepository.save(skillRegistry.getHistoryType(), finalChatId);

        ChatClient chatClient = chatClientResolver.resolveByBeanName(profile.clientBean());
        if (profile.mode() == SkillMode.SYNC) {
            // 对于同步模式技能，流式接口降级为单块输出，确保前端协议一致。
            return Flux.just(safeSyncCall(chatClient, prompt, finalChatId, profile.code()));
        }
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, finalChatId))
                .stream()
                .content()
                .onErrorResume(ex -> {
                    log.error("Skill stream call failed. skill={}, chatId={}, reason={}", profile.code(), finalChatId, ex.getMessage(), ex);
                    return Flux.just("技能服务暂不可用，请检查模型服务地址与可用性后重试。");
                });
    }

    @Override
    public String syncChat(String skillCode, String prompt, String chatId) {
        validatePrompt(prompt);

        SkillProfile profile = skillRegistry.getRequiredEnabled(skillCode);
        String finalChatId = StringUtils.hasText(chatId) ? chatId.trim() : nextChatId(skillCode);
        chatHistoryRepository.save(skillRegistry.getHistoryType(), finalChatId);

        ChatClient chatClient = chatClientResolver.resolveByBeanName(profile.clientBean());
        return safeSyncCall(chatClient, prompt, finalChatId, profile.code());
    }

    @Override
    public String nextChatId(String skillCode) {
        SkillProfile profile = skillRegistry.getRequiredEnabled(skillCode);
        String prefix = StringUtils.hasText(profile.conversationPrefix())
                ? profile.conversationPrefix()
                : skillRegistry.getDefaultConversationPrefix();
        return prefix + "_" + profile.code() + "_" + System.currentTimeMillis();
    }

    private String syncCall(ChatClient chatClient, String prompt, String chatId) {
        String content = chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .call()
                .content();
        return content == null ? "" : content;
    }

    private String safeSyncCall(ChatClient chatClient, String prompt, String chatId, String skillCode) {
        try {
            return syncCall(chatClient, prompt, chatId);
        } catch (Exception ex) {
            log.error("Skill sync call failed. skill={}, chatId={}, reason={}", skillCode, chatId, ex.getMessage(), ex);
            throw new SkillException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MODEL_SERVICE_UNAVAILABLE",
                    "Skill model service unavailable. Please check model endpoint configuration and retry."
            );
        }
    }

    private void validatePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new SkillException(HttpStatus.BAD_REQUEST, "PROMPT_EMPTY", "Prompt must not be empty.");
        }
    }

    private SkillVO toSkillVO(SkillProfile profile) {
        return new SkillVO(
                profile.code(),
                profile.name(),
                profile.description(),
                profile.mode(),
                profile.conversationPrefix(),
                profile.welcomeMessage()
        );
    }
}
