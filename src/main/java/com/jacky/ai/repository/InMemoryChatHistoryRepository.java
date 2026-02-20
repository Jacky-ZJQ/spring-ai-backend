package com.jacky.ai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: Jacky.Z
 * @date: 2025/6/21 10:04
 * @description： 会话历史-内存中存储
 */
@RequiredArgsConstructor
@Component
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    // 会话历史-内存中存储
    // key: 业务类型（如 "chat", "service", "pdf"） value: 该业务类型的所有会话ID列表
    Map<String, List<String>> chatHistory = new HashMap<>();

    @Override
    public void save(String type, String chatId) {
        // 目前我们业务比较简单，没有用户概念，但是将来会有不同业务，因此简单采用内存保存type与chatId关系。
        // TODO 将来也可以根据业务需要把会话id持久化保存到Redis、MongoDB、MySQL等数据库。
        // TODO 如果业务中有user的概念，还需要记录userId、chatId、time等关联关系
        List<String> chatIds = chatHistory.computeIfAbsent(type, k -> new ArrayList<>());
        if (!chatIds.contains(chatId)) {
            chatIds.add(chatId);
        }
    }

    @Override
    public List<String> getChatIds(String type) {
        return chatHistory.getOrDefault(type, List.of());
    }
}
