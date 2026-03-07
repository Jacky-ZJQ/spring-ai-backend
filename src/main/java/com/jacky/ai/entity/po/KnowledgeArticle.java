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
@TableName("knowledge_article")
public class KnowledgeArticle implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("summary")
    private String summary;

    @TableField("type")
    private String type;

    @TableField("category_id")
    private Long categoryId;

    @TableField("content_md")
    private String contentMd;

    @TableField("extra_json")
    private String extraJson;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("visibility")
    private String visibility;

    @TableField("status")
    private String status;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("favorite_count")
    private Integer favoriteCount;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("deleted")
    private Integer deleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
