package com.jacky.ai.entity.query;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.List;

@Data
/**
 * 课程查询条件（供 LLM 工具调用）。
 */
public class CourseQuery {

    /**
     * 课程分类过滤条件。
     */
    @ToolParam(required = false, description = "课程分类：咖啡技艺、门店管理、品鉴认证、文化沙龙")
    private String type;

    /**
     * 适配等级上限，工具内部用 <= edu 过滤。
     */
    @ToolParam(required = false, description = "学习等级：0-无要求、1-初级、2-中级、3-高级、4-大师")
    private Integer edu;

    /**
     * 排序规则列表，支持多字段排序。
     */
    @ToolParam(required = false, description = "排序方式")
    private List<Sort> sorts;

    @Data
    public static class Sort {
        /**
         * 排序字段白名单：price/duration/name/edu/type。
         */
        @ToolParam(required = false, description = "排序字段: price/duration/name/edu/type")
        private String field;

        /**
         * 是否升序；null 时默认按升序处理。
         */
        @ToolParam(required = false, description = "是否是升序: true/false")
        private Boolean asc;
    }
}