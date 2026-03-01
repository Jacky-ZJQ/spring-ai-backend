package com.jacky.ai.mcp.tools;

import com.jacky.ai.entity.po.Course;
import com.jacky.ai.entity.query.CourseQuery;
import com.jacky.ai.mcp.McpArgUtils;
import com.jacky.ai.mcp.McpTool;
import com.jacky.ai.tools.CourseTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CourseQueryMcpTool implements McpTool {

    private final CourseTools courseTools;

    @Override
    public String name() {
        return "course_query";
    }

    @Override
    public String description() {
        return "按课程类型、等级与排序条件查询咖啡课程";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "description", "Course category"
                        ),
                        "edu", Map.of(
                                "type", "integer",
                                "description", "Learning level upper bound: 0-4"
                        ),
                        "sorts", Map.of(
                                "type", "array",
                                "description", "Sort conditions",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "field", Map.of(
                                                        "type", "string",
                                                        "enum", List.of("price", "duration", "name", "edu", "type")
                                                ),
                                                "asc", Map.of(
                                                        "type", "boolean",
                                                        "description", "true for asc, false for desc"
                                                )
                                        ),
                                        // 每个排序项至少要包含 field 字段。
                                        "required", List.of("field")
                                )
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Max returned rows, default 20, range 1-100"
                        )
                )
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        // 将宽松类型的 MCP 参数转换为现有领域查询模型。
        CourseQuery query = new CourseQuery();
        query.setType(McpArgUtils.optionalString(arguments, "type"));
        query.setEdu(McpArgUtils.optionalInt(arguments, "edu"));

        List<Map<String, Object>> sortItems = McpArgUtils.optionalObjectList(arguments, "sorts");
        if (!sortItems.isEmpty()) {
            List<CourseQuery.Sort> sorts = new ArrayList<>(sortItems.size());
            for (Map<String, Object> sortItem : sortItems) {
                CourseQuery.Sort sort = new CourseQuery.Sort();
                sort.setField(McpArgUtils.optionalString(sortItem, "field"));
                sort.setAsc(McpArgUtils.optionalBoolean(sortItem, "asc"));
                sorts.add(sort);
            }
            query.setSorts(sorts);
        }

        boolean emptyQuery = query.getType() == null && query.getEdu() == null
                && (query.getSorts() == null || query.getSorts().isEmpty());

        // 限制返回条数，避免工具响应体无限膨胀。
        Integer requestedLimit = McpArgUtils.optionalInt(arguments, "limit");
        int limit = McpArgUtils.clamp(requestedLimit == null ? 20 : requestedLimit, 1, 100);

        List<Course> courses = courseTools.queryCourse(emptyQuery ? null : query);
        return courses.stream().limit(limit).toList();
    }
}
