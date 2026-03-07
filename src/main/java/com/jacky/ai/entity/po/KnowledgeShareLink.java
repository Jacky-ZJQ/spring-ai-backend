package com.jacky.ai.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("knowledge_share_link")
public class KnowledgeShareLink implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("share_token")
    private String shareToken;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("status")
    private String status;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
