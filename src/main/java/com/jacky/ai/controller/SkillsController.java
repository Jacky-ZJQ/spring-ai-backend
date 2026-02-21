package com.jacky.ai.controller;

import com.jacky.ai.entity.query.SkillChatRequest;
import com.jacky.ai.entity.vo.SkillChatVO;
import com.jacky.ai.entity.vo.SkillVO;
import com.jacky.ai.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@CrossOrigin("*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/skills")
public class SkillsController {

    private final SkillService skillService;

    /**
     * 查询已启用的技能列表。
     */
    @GetMapping
    public List<SkillVO> listSkills() {
        return skillService.listSkills();
    }

    /**
     * 查询单个技能信息。
     */
    @GetMapping("/{skillCode}")
    public SkillVO getSkill(@PathVariable String skillCode) {
        return skillService.getSkill(skillCode);
    }

    /**
     * 同步对话接口，适用于普通 HTTP JSON 请求。
     */
    @PostMapping("/{skillCode}/chat")
    public SkillChatVO syncChat(@PathVariable String skillCode,
                                @RequestBody(required = false) SkillChatRequest request) {
        SkillChatRequest req = request == null ? new SkillChatRequest() : request;
        String finalChatId = req.getChatId() != null && !req.getChatId().isBlank()
                ? req.getChatId().trim()
                : skillService.nextChatId(skillCode);
        String content = skillService.syncChat(skillCode, req.getPrompt(), finalChatId);
        return new SkillChatVO(skillCode, finalChatId, content);
    }

    /**
     * 流式对话接口，返回纯文本分块流。
     */
    @PostMapping(value = "/{skillCode}/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Flux<String>> streamChat(@PathVariable String skillCode,
                                                   @RequestBody(required = false) SkillChatRequest request) {
        SkillChatRequest req = request == null ? new SkillChatRequest() : request;
        String finalChatId = req.getChatId() != null && !req.getChatId().isBlank()
                ? req.getChatId().trim()
                : skillService.nextChatId(skillCode);
        Flux<String> content = skillService.streamChat(skillCode, req.getPrompt(), finalChatId);
        return ResponseEntity.ok()
                .header("X-Chat-Id", finalChatId)
                .contentType(MediaType.parseMediaType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8"))
                .body(content);
    }
}
