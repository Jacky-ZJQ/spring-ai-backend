package com.jacky.ai.service;


import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * @author: Jacky.Z
 * @date: 2025/6/21 09:19
 * @description： SpringAI自带了会话记忆功能，可以帮我们把历史会话保存下来，下一次请求AI时会自动拼接，非常方便。
 * 会话记忆功能是基于AOP实现的，Spring提供了一个`MessageChatMemoryAdvisor`的通知，我们可以像之前添加日志通知一样添加到`ChatClient`即可。
 */
public interface ChatMemory {

    // 添加消息到指定会话的历史消息
    default void add(String conversationId, Message message){
        this.add(conversationId, List.of(message));
    }

    // 添加消息到指定会话的历史消息
    void add(String conversationId, List<Message> message);

    // 获取指定会话的历史消息
    List<Message> get(String conversationId);

    // 指定消息删除
    void clear(String conversationId, Message message);


}
