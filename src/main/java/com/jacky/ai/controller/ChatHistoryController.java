package com.jacky.ai.controller;

import com.jacky.ai.entity.ChatImageMeta;
import com.jacky.ai.entity.MessageFileVO;
import com.jacky.ai.entity.MessageVO;
import com.jacky.ai.repository.ChatImageRepository;
import com.jacky.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author: Jacky.Z
 * @date: 2025/6/21 10:27
 * @description：查询会话历史
 */
@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/history")
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatMemory chatMemory;

    private final ChatImageRepository chatImageRepository;

    private final Logger logger = LoggerFactory.getLogger(ChatHistoryController.class);

    /**
     * 对外 API 前缀（默认 /api），用于组装前端可访问的附件 URL。
     */
    @Value("${app.api-prefix:/api}")
    private String apiPrefix;

    /**
     * 1、查询会话历史ID列表
     * @param type 业务类型，如：chat,service,pdf
     * @return chatId列表
     */
    @GetMapping("/{type}")
    public List<String> getChatIds(@PathVariable("type") String type){
        logger.info("查询会话历史列表，业务类型：{}", type);
        List<String> getChatIds = chatHistoryRepository.getChatIds(type);
        logger.info("查询会话历史列表，结果：{}", getChatIds);
        return getChatIds;
    }

    /**
     * 2、查询单个会话历史消息
     * @param type 业务类型，如：chat,service,pdf
     * @param chatId 会话id
     * @return 指定会话的历史消息
     */
    @GetMapping("/{type}/{chatId}")
    public List<MessageVO> getChatHistory(@PathVariable("type") String type, @PathVariable("chatId") String chatId) {
        logger.info("查询会话历史，业务类型：{}, 会话id：{}", type, chatId);
        List<Message> messages = chatMemory.get(chatId,Integer.MAX_VALUE);
        if(messages == null) {
            return List.of();
        }
        // 仅普通 AI 聊天需要回填图片附件，其他业务类型保持原有消息结构。
        boolean attachImages = "chat".equalsIgnoreCase(type);
        Map<Integer, List<ChatImageMeta>> chatImagesByTurn = attachImages ? chatImageRepository.getByChatId(chatId) : Map.of();

        // 转换为 VO 对象，并按“用户轮次”补齐图片附件。
        int userTurn = 0;
        List<MessageVO> list = new ArrayList<>(messages.size());
        for (Message message : messages) {
            MessageVO messageVO = new MessageVO(message);
            if (attachImages && "user".equals(messageVO.getRole())) {
                userTurn++;
                messageVO.setFiles(toMessageFiles(chatId, chatImagesByTurn.get(userTurn)));
            }
            list.add(messageVO);
        }
        logger.info("查询会话历史，业务类型：{},结果：{}", type, list);
        return list;
    }

    private List<MessageFileVO> toMessageFiles(String chatId, List<ChatImageMeta> imageMetas) {
        if (imageMetas == null || imageMetas.isEmpty()) {
            return null;
        }
        String normalizedApiPrefix = normalizeApiPrefix(apiPrefix);
        String encodedChatId = UriUtils.encodePathSegment(chatId, StandardCharsets.UTF_8);
        return imageMetas.stream().map(meta -> {
            // 路径段编码后再拼接 URL，避免文件名特殊字符造成路由解析问题。
            String encodedStoredName = UriUtils.encodePathSegment(meta.getStoredName(), StandardCharsets.UTF_8);
            String url = normalizedApiPrefix + "/ai/chat/attachments/" + encodedChatId + "/" + encodedStoredName;
            return new MessageFileVO(meta.getOriginalName(), meta.getContentType(), meta.getSize(), url);
        }).toList();
    }

    private String normalizeApiPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
