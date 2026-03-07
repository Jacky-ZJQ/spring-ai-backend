package com.jacky.ai.entity.dto.knowledge.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeArticleDetail {

    private Long id;

    private String title;

    private String summary;

    private String type;

    private String status;

    private String visibility;

    private Long categoryId;

    private String categoryName;

    private String contentMd;

    private String extraJson;

    private String coverUrl;

    private Integer viewCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<KnowledgeTagItem> tags;
}
