package com.jacky.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话图片元数据（仅用于历史恢复，不直接返回给前端）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatImageMeta {

    /**
     * 原始文件名（用于展示）
     */
    private String originalName;

    /**
     * 存储文件名（用于定位文件）
     */
    private String storedName;

    /**
     * MIME 类型（image/png、image/jpeg 等）
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private long size;
}

