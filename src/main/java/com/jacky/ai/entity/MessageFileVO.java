package com.jacky.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息文件展示对象（返回给前端）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageFileVO {

    /**
     * 原始文件名
     */
    private String name;

    /**
     * MIME 类型
     */
    private String type;

    /**
     * 文件大小（字节）
     */
    private long size;

    /**
     * 前端可访问 URL
     */
    private String url;
}

