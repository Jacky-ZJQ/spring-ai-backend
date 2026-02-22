package com.jacky.ai.controller;


import com.jacky.ai.entity.ChatImageMeta;
import com.jacky.ai.repository.ChatImageRepository;
import com.jacky.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.model.Media;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.net.URLEncoder.encode;
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

    private final ChatImageRepository chatImageRepository;

    private final ChatMemory chatMemory;

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
        // 在请求模型前先计算并保存“用户轮次附件”，避免流式响应中断导致轮次错位。
        int userTurn = resolveNextUserTurn(chatId);
        chatImageRepository.save(chatId, userTurn, files);

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

    /**
     * 读取聊天图片附件，供前端历史消息直接渲染。
     */
    @GetMapping("/chat/attachments/{chatId}/{storedName}")
    public ResponseEntity<Resource> getChatAttachment(@PathVariable("chatId") String chatId,
                                                      @PathVariable("storedName") String storedName) {
        // 先走元数据校验，再读取文件，避免直接拼路径读取导致越权访问。
        Optional<ChatImageMeta> imageMetaOpt = chatImageRepository.findMeta(chatId, storedName);
        if (imageMetaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = chatImageRepository.getFile(chatId, storedName);
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        ChatImageMeta imageMeta = imageMetaOpt.get();
        String encodedFileName = encode(
                Objects.requireNonNullElse(imageMeta.getOriginalName(), "image"),
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encodedFileName + "\"")
                .contentType(resolveContentType(imageMeta.getContentType()))
                .body(resource);
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

    /**
     * 统计当前会话已存在的用户消息条数，计算“下一轮用户发言序号”。
     */
    private int resolveNextUserTurn(String chatId) {
        List<Message> history = chatMemory.get(chatId, Integer.MAX_VALUE);
        if (history == null || history.isEmpty()) {
            return 1;
        }
        long userCount = history.stream()
                .filter(message -> MessageType.USER == message.getMessageType())
                .count();
        return (int) userCount + 1;
    }

    private MediaType resolveContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }


}
