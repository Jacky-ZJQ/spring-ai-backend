package com.jacky.ai.repository;


import org.springframework.stereotype.Service;
import java.util.List;

/**
 * @author: Jacky.Z
 * @date: 2025/6/21 10:05
 * @description： 会话历史记录存储接口
 */
@Service
public interface ChatHistoryRepository {

    /**
     * 保存聊天记录
     * @param type 业务类型：如：chat、service、pdf
     * @param chatId 会话ID
     */
    void save(String type, String chatId);

    /**
     * 根据业务类型获取会话ID列表
     * @param type 业务类型：如：chat、service、pdf
     * @return 会话ID列表
     */
    List<String> getChatIds(String type);

    /**
     * 清空所有会话历史。
     *
     * @return 被清理的会话数量
     */
    default int clearAll() {
        return 0;
    }

}
