package com.jacky.ai.entity.dto.knowledge.request;

import lombok.Data;

@Data
public class KnowledgeArticleQueryRequest {

    private Integer pageNo;

    private Integer pageSize;

    private String q;

    private String type;

    private Long categoryId;

    private Long tagId;

    private String sort;

    private String status;
}
