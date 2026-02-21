package com.jacky.ai.mcp.tool.impl;

import com.jacky.ai.entity.query.CourseQuery;
import com.jacky.ai.mcp.tool.McpToolHandler;
import com.jacky.ai.mcp.tool.support.McpArgumentHelper;
import com.jacky.ai.tools.CourseTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * MCP 工具：query_course。
 */
@Component
@RequiredArgsConstructor
public class QueryCourseMcpToolHandler implements McpToolHandler {

    private final CourseTools courseTools;

    @Override
    public String name() {
        return "query_course";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        CourseQuery query = new CourseQuery();
        query.setType(McpArgumentHelper.nullableString(arguments.get("type")));
        Object eduObj = arguments.get("edu");
        if (eduObj instanceof Number number) {
            query.setEdu(number.intValue());
        } else if (eduObj instanceof String text && StringUtils.hasText(text)) {
            query.setEdu(Integer.parseInt(text.trim()));
        }
        return courseTools.queryCourse(query);
    }
}
