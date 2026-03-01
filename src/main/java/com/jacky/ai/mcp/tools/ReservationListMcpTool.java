package com.jacky.ai.mcp.tools;

import com.jacky.ai.entity.po.CourseReservation;
import com.jacky.ai.mcp.McpArgUtils;
import com.jacky.ai.mcp.McpTool;
import com.jacky.ai.service.ICourseReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationListMcpTool implements McpTool {

    private final ICourseReservationService courseReservationService;

    @Override
    public String name() {
        return "reservation_list";
    }

    @Override
    public String description() {
        return "查询最新课程预约列表";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Default 10, max 50"
                        )
                )
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        // 与现有 HTTP 接口保持一致：默认 10，最大 50。
        Integer requestedLimit = McpArgUtils.optionalInt(arguments, "limit");
        int limit = McpArgUtils.clamp(requestedLimit == null ? 10 : requestedLimit, 1, 50);
        try {
            return courseReservationService.lambdaQuery()
                    .orderByDesc(CourseReservation::getId)
                    .last("limit " + limit)
                    .list();
        } catch (Exception ex) {
            // 兼容历史表结构（可能缺少 created_at 等字段）时的降级查询。
            log.warn("Query reservations with full columns failed, fallback to compatible columns. error={}", ex.getMessage());
            return courseReservationService.lambdaQuery()
                    .select(CourseReservation::getId,
                            CourseReservation::getCourse,
                            CourseReservation::getStudentName,
                            CourseReservation::getContactInfo,
                            CourseReservation::getSchool,
                            CourseReservation::getRemark)
                    .orderByDesc(CourseReservation::getId)
                    .last("limit " + limit)
                    .list();
        }
    }
}
