package com.jacky.ai.entity.dto.knowledge.response;

import lombok.Data;

@Data
public class KnowledgeCategoryItem {

    private Long id;

    private String name;

    private String code;

    private Integer sortOrder;
}
