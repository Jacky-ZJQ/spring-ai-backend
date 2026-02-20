package com.jacky.ai.controller;

import com.jacky.ai.entity.MessageVO;
import com.jacky.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    private final Logger logger = LoggerFactory.getLogger(ChatHistoryController.class);

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
        // 转换为VO对象
        List<MessageVO> list = messages.stream().map(MessageVO::new).toList();
        logger.info("查询会话历史，业务类型：{},结果：{}", type, list);
        return list;
    }

}
