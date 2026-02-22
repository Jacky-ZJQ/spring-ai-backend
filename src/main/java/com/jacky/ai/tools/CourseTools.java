package com.jacky.ai.tools;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.jacky.ai.entity.po.Course;
import com.jacky.ai.entity.po.CourseReservation;
import com.jacky.ai.entity.po.School;
import com.jacky.ai.entity.query.CourseQuery;
import com.jacky.ai.service.ICourseReservationService;
import com.jacky.ai.service.ICourseService;
import com.jacky.ai.service.ISchoolService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
/**
 * 课程咨询/预约工具集合，供大模型通过 Tool Calling 调用。
 */
public class CourseTools {

    private final ICourseService courseService;
    private final ISchoolService schoolService;
    private final ICourseReservationService courseReservationService;
    private final static Logger logger = LoggerFactory.getLogger(CourseTools.class);

    /**
     * 允许排序的字段白名单，避免任意字段拼接导致 SQL 注入风险。
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "duration", "name", "edu", "type");

    @Tool(description = "根据课程分类(type)和学习等级(edu)查询星巴克咖啡课程")
    public List<Course> queryCourse(@ToolParam(required = false, description = "课程查询条件") CourseQuery query) {
        logger.info("[LLM-Tool]CourseTools.queryCourse根据条件查询课程: {}", query);
        QueryChainWrapper<Course> wrapper = courseService.query();
        if (query != null) {
            wrapper
                    .eq(query.getType() != null && !query.getType().trim().isEmpty(), "type", query.getType().trim())
                    .le(query.getEdu() != null, "edu", query.getEdu());
        }
        if (query != null && query.getSorts() != null) {
            for (CourseQuery.Sort sort : query.getSorts()) {
                if (sort == null || sort.getField() == null || !ALLOWED_SORT_FIELDS.contains(sort.getField())) {
                    continue;
                }
                boolean asc = sort.getAsc() == null || sort.getAsc();
                wrapper.orderBy(true, asc, sort.getField());
            }
        }
        return wrapper.list();
    }

    @Tool(description = "查询所有星巴克门店")
    public List<School> queryAllSchools() {
        logger.info("[LLM-Tool]CourseTools.queryAllSchools查询所有门店");
        return schoolService.list();
    }

    @Tool(description = "生成课程预约单,并返回预约编号")
    public String generateCourseReservation(String courseName, String studentName,
                                            String contactInfo, String school, String remark) {

        logger.info("[LLM-Tool]CourseTools.generateCourseReservation生成课程预约单, courseName: {}, studentName: {}, contactInfo: {}, school: {}, remark: {}", courseName, studentName, contactInfo, school, remark);
        CourseReservation courseReservation = new CourseReservation();
        courseReservation.setCourse(courseName);
        courseReservation.setStudentName(studentName);
        courseReservation.setContactInfo(contactInfo);
        courseReservation.setSchool(school);
        courseReservation.setRemark(remark);
        // save 后由数据库回填自增主键，返回该 id 作为预约编号。
        courseReservationService.save(courseReservation);
        return String.valueOf(courseReservation.getId());
    }
}