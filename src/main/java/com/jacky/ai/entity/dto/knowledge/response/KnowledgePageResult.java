package com.jacky.ai.entity.dto.knowledge.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePageResult<T> {

    private List<T> items;

    private Long total;

    private Integer pageNo;

    private Integer pageSize;

    public static <T> KnowledgePageResult<T> of(List<T> items, long total, int pageNo, int pageSize) {
        return new KnowledgePageResult<>(items, total, pageNo, pageSize);
    }
}
