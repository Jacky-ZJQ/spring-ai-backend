package com.jacky.ai.mcp.tools;

import com.jacky.ai.entity.po.School;
import com.jacky.ai.mcp.McpArgUtils;
import com.jacky.ai.mcp.McpTool;
import com.jacky.ai.service.ISchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SchoolListMcpTool implements McpTool {

    private final ISchoolService schoolService;

    @Override
    public String name() {
        return "school_list";
    }

    @Override
    public String description() {
        return "查询星巴克学校列表，可按城市筛选";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "Filter schools by city"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Max returned rows, default 50, range 1-100"
                        )
                )
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String city = McpArgUtils.optionalString(arguments, "city");
        // 控制列表规模，保证 MCP 响应稳定性。
        Integer requestedLimit = McpArgUtils.optionalInt(arguments, "limit");
        int limit = McpArgUtils.clamp(requestedLimit == null ? 50 : requestedLimit, 1, 100);

        return schoolService.lambdaQuery()
                .eq(city != null, School::getCity, city)
                .orderByAsc(School::getId)
                .last("limit " + limit)
                .list();
    }
}
