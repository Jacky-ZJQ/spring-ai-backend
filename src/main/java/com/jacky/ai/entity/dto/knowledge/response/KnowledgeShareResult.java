package com.jacky.ai.entity.dto.knowledge.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeShareResult {

    private Long articleId;

    private String shareToken;

    private String sharePath;

    private LocalDateTime expireAt;
}
