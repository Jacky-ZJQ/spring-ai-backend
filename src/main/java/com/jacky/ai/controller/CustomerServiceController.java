package com.jacky.ai.controller;

import com.jacky.ai.entity.po.CourseReservation;
import com.jacky.ai.repository.ChatHistoryRepository;
import com.jacky.ai.service.ICourseReservationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class CustomerServiceController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerServiceController.class);
    private final ChatClient serviceOpenAiChatClient;

    private final ChatHistoryRepository chatHistoryRepository;
    private final ICourseReservationService courseReservationService;

    /**
     * 1、客服聊天
     * @param prompt 提示词
     * @param chatId 会话id
     * @return 响应流chatId
     */
    @RequestMapping(value = "/service", produces = "text/html;charset=utf-8")
    public String service(String prompt, String chatId) {
        // 1.保存会话id
        chatHistoryRepository.save("service", chatId);
        // 2.请求模型
        return serviceOpenAiChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .call()
                .content();
    }

    /**
     * 2、查询最新预约单列表
     * @param limit 返回条数，默认10，最大50
     * @return 预约单列表
     */
    @RequestMapping(value = "/service/reservations", produces = "application/json;charset=utf-8")
    public List<CourseReservation> reservations(Integer limit) {
        // 限制查询条数，避免一次返回过多数据影响接口性能。
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        try {
            // 优先查完整字段（包含 created_at），适用于新库结构。
            return courseReservationService.lambdaQuery()
                    .orderByDesc(CourseReservation::getId)
                    .last("limit " + safeLimit)
                    .list();
        } catch (Exception ex) {
            // 兼容历史库：部分旧库缺少 created_at 字段时，降级查询基础字段。
            logger.warn("Query reservations with full columns failed, fallback to compatible columns. error={}", ex.getMessage());
            return courseReservationService.lambdaQuery()
                    .select(CourseReservation::getId,
                            CourseReservation::getCourse,
                            CourseReservation::getStudentName,
                            CourseReservation::getContactInfo,
                            CourseReservation::getSchool,
                            CourseReservation::getRemark)
                    .orderByDesc(CourseReservation::getId)
                    .last("limit " + safeLimit)
                    .list();
        }
    }
}
