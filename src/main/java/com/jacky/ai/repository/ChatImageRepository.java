package com.jacky.ai.repository;

import com.jacky.ai.entity.ChatImageMeta;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 聊天图片仓储，用于“刷新后恢复图片”能力。
 */
public interface ChatImageRepository {

    /**
     * 保存某个会话指定轮次的图片。
     *
     * @param chatId 会话 id
     * @param userTurn 第几轮用户发言（从 1 开始）
     * @param files 上传文件
     * @return 实际保存成功的图片元数据
     */
    List<ChatImageMeta> save(String chatId, int userTurn, List<MultipartFile> files);

    /**
     * 查询会话下所有轮次的图片映射。
     *
     * @param chatId 会话 id
     * @return key=轮次, value=该轮图片列表
     */
    Map<Integer, List<ChatImageMeta>> getByChatId(String chatId);

    /**
     * 读取会话图片文件。
     *
     * @param chatId 会话 id
     * @param storedName 存储文件名
     * @return 文件资源
     */
    Resource getFile(String chatId, String storedName);

    /**
     * 根据存储文件名查询元数据。
     */
    Optional<ChatImageMeta> findMeta(String chatId, String storedName);
}

