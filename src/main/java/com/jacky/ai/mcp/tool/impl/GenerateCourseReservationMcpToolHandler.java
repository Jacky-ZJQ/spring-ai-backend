package com.jacky.ai.mcp.tool.impl;

import com.jacky.ai.mcp.tool.McpToolHandler;
import com.jacky.ai.mcp.tool.support.McpArgumentHelper;
import com.jacky.ai.tools.CourseTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 工具：generate_course_reservation。
 */
@Component
@RequiredArgsConstructor
public class GenerateCourseReservationMcpToolHandler implements McpToolHandler {

    private final CourseTools courseTools;

    @Override
    public String name() {
        return "generate_course_reservation";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return courseTools.generateCourseReservation(
                McpArgumentHelper.requiredString(arguments, "courseName"),
                McpArgumentHelper.requiredString(arguments, "studentName"),
                McpArgumentHelper.requiredString(arguments, "contactInfo"),
                McpArgumentHelper.requiredString(arguments, "school"),
                McpArgumentHelper.nullableString(arguments.get("remark"))
        );
    }
}
