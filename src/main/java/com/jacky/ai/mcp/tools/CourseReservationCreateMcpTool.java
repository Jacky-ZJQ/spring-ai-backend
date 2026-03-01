package com.jacky.ai.mcp.tools;

import com.jacky.ai.entity.po.CourseReservation;
import com.jacky.ai.mcp.McpArgUtils;
import com.jacky.ai.mcp.McpTool;
import com.jacky.ai.service.ICourseReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CourseReservationCreateMcpTool implements McpTool {

    private final ICourseReservationService courseReservationService;

    @Override
    public String name() {
        return "reservation_create";
    }

    @Override
    public String description() {
        return "创建课程预约并返回预约编号";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "courseName", Map.of("type", "string", "description", "Course name"),
                        "studentName", Map.of("type", "string", "description", "Student name"),
                        "contactInfo", Map.of("type", "string", "description", "Phone or contact info"),
                        "school", Map.of("type", "string", "description", "School name"),
                        "remark", Map.of("type", "string", "description", "Optional remark")
                ),
                "required", java.util.List.of("courseName", "studentName", "contactInfo", "school")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        // 在这里校验必填字段，返回清晰的工具级错误信息。
        String courseName = McpArgUtils.requiredString(arguments, "courseName");
        String studentName = McpArgUtils.requiredString(arguments, "studentName");
        String contactInfo = McpArgUtils.requiredString(arguments, "contactInfo");
        String school = McpArgUtils.requiredString(arguments, "school");
        String remark = McpArgUtils.optionalString(arguments, "remark");

        CourseReservation reservation = new CourseReservation();
        reservation.setCourse(courseName);
        reservation.setStudentName(studentName);
        reservation.setContactInfo(contactInfo);
        reservation.setSchool(school);
        reservation.setRemark(remark);
        // save() 后会回填自增主键，作为 reservationId 返回。
        courseReservationService.save(reservation);

        return Map.of(
                "reservationId", reservation.getId(),
                "message", "Reservation created"
        );
    }
}
