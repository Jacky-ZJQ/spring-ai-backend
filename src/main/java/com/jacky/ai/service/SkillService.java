package com.jacky.ai.service;

import com.jacky.ai.entity.vo.SkillVO;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SkillService {

    List<SkillVO> listSkills();

    SkillVO getSkill(String skillCode);

    Flux<String> streamChat(String skillCode, String prompt, String chatId);

    String syncChat(String skillCode, String prompt, String chatId);

    String nextChatId(String skillCode);
}
