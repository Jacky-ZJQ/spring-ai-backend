package com.jacky.ai.entity.dto.knowledge.request;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeArticleSaveRequest {

    private String title;

    private String summary;

    private String type;

    private Long categoryId;

    private List<Long> tagIds;

    private String contentMd;

    private String extraJson;

    private String coverUrl;

    private String visibility;

    private String status;

    private String createdBy;

    private String updatedBy;
}
