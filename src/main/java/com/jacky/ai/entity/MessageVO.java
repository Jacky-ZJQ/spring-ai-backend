package com.jacky.ai.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;

/**
 * @author: Jacky.Z
 * @date: 2025/6/21 10:23
 * @description：
 */
@NoArgsConstructor
@Data
public class MessageVO {

    private String role;
    private String content;

    public MessageVO(Message message) {
        this.role = switch (message.getMessageType()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            default -> "";
        };
        this.content = message.getText();
    }

}
