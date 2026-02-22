package com.jacky.ai.controller;


import com.jacky.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.Media;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Objects;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * @author: Jacky.Z
 * @date: 2025/6/17 22:28
 * @description：
 */
@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient ollamaChatClient;

    private final ChatClient openAiChatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    /**
     * 可选值：ollama / openai
     * 默认使用 openai，避免线上没有 Ollama 时 /ai/chat 不可用
     */
    @Value("${spring.ai.chat.text-provider:openai}")
    private String textProvider;

    @RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")
    public Flux<String> chat(@RequestParam("prompt") String prompt,
                             @RequestParam("chatId") String chatId,
                             @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        // 1.保存会话id
        chatHistoryRepository.save("chat", chatId);

        // 2.请求模型（多模态)
        if (files == null || files.isEmpty()) {
            // 没有附件，纯文本聊天
            return textChat(prompt, chatId);
        } else {
            // 有附件，多模态聊天
            return multiModalChat(prompt, chatId, files);
        }
    }


    /**
     * 2.多模态聊天
     * @param prompt 提示词
     * @param chatId 会话id
     * @param files 附件列表
     * @return 响应流chatId
     */
    private Flux<String> multiModalChat(String prompt, String chatId, List<MultipartFile> files) {
        // 1.解析多媒体
        List<Media> medias = files.stream()
                .map(file -> new Media(
                                MimeType.valueOf(Objects.requireNonNull(file.getContentType())),
                                file.getResource()))
                .toList();
        // 2.请求模型
        return openAiChatClient.prompt()
                .user(p -> p.text(prompt).media(medias.toArray(Media[]::new)))
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    //同一个会话ID的聊天内容连续存储的关键：
    //1、chatId作为唯一标识：每次请求都携带相同的chatId
    //2、MessageChatMemoryAdvisor自动管理：
    //  请求前：从ChatMemory.get(chatId)获取历史消息
    //  请求后：通过ChatMemory.add(chatId, newMessages)追加新消息
    //3、InMemoryChatMemory内部使用Map：Map<String, List<Message>>，key是chatId，value是消息列表
    //4、消息列表不断追加：每次对话都会将新的user消息和assistant消息追加到列表末尾
    //这种设计使得AI能够"记住"之前的对话内容，实现多轮对话的上下文连贯性。

    /**
     * 1.纯文本聊天
     * @param prompt 提示词
     * @param chatId 会话id
     * @return 响应流
     */
    private Flux<String> textChat(String prompt, String chatId) {
        ChatClient textChatClient = "ollama".equalsIgnoreCase(textProvider) ? ollamaChatClient : openAiChatClient;
        return textChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)) // 传递chatId给Advisor的方式是通过AdvisorContext，也就是以key-value形式存入上下文
                .stream()
                .content();
    }


}
