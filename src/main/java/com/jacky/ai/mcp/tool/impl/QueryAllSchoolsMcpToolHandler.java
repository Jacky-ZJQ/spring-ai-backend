package com.jacky.ai.mcp.tool.impl;

import com.jacky.ai.mcp.tool.McpToolHandler;
import com.jacky.ai.tools.CourseTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 工具：query_all_schools。
 */
@Component
@RequiredArgsConstructor
public class QueryAllSchoolsMcpToolHandler implements McpToolHandler {

    private final CourseTools courseTools;

    @Override
    public String name() {
        return "query_all_schools";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return courseTools.queryAllSchools();
    }
}
