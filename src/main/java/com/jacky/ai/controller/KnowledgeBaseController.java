package com.jacky.ai.controller;

import com.jacky.ai.entity.dto.knowledge.request.KnowledgeArticleQueryRequest;
import com.jacky.ai.entity.dto.knowledge.request.KnowledgeArticleSaveRequest;
import com.jacky.ai.service.KnowledgeBaseManageService;
import com.jacky.ai.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/knowledge")
public class KnowledgeBaseController {

    private final KnowledgeBaseManageService knowledgeBaseManageService;

    @GetMapping("/articles")
    public Map<String, Object> listArticles(KnowledgeArticleQueryRequest request) {
        return ResponseUtil.success(knowledgeBaseManageService.listArticles(request), "查询成功");
    }

    @GetMapping("/articles/{id}")
    public Map<String, Object> getArticle(@PathVariable("id") Long id) {
        return ResponseUtil.success(knowledgeBaseManageService.getArticleDetail(id), "查询成功");
    }

    @PostMapping("/articles")
    public Map<String, Object> createArticle(@RequestBody KnowledgeArticleSaveRequest request) {
        return ResponseUtil.success(knowledgeBaseManageService.createArticle(request), "创建成功");
    }

    @PutMapping("/articles/{id}")
    public Map<String, Object> updateArticle(@PathVariable("id") Long id,
                                             @RequestBody KnowledgeArticleSaveRequest request) {
        return ResponseUtil.success(knowledgeBaseManageService.updateArticle(id, request), "更新成功");
    }

    @DeleteMapping("/articles/{id}")
    public Map<String, Object> deleteArticle(@PathVariable("id") Long id) {
        knowledgeBaseManageService.deleteArticle(id);
        return ResponseUtil.success(null, "删除成功");
    }

    @PostMapping("/articles/{id}/publish")
    public Map<String, Object> publishArticle(@PathVariable("id") Long id) {
        return ResponseUtil.success(knowledgeBaseManageService.publishArticle(id), "发布成功");
    }

    @PostMapping("/articles/{id}/unpublish")
    public Map<String, Object> unpublishArticle(@PathVariable("id") Long id) {
        return ResponseUtil.success(knowledgeBaseManageService.unpublishArticle(id), "下架成功");
    }

    @PostMapping("/articles/{id}/like")
    public Map<String, Object> likeArticle(@PathVariable("id") Long id) {
        return ResponseUtil.success(knowledgeBaseManageService.likeArticle(id), "点赞成功");
    }

    @GetMapping("/categories")
    public Map<String, Object> listCategories() {
        return ResponseUtil.success(knowledgeBaseManageService.listCategories(), "查询成功");
    }

    @GetMapping("/tags")
    public Map<String, Object> listTags() {
        return ResponseUtil.success(knowledgeBaseManageService.listTags(), "查询成功");
    }

    @PostMapping("/articles/{id}/share")
    public Map<String, Object> createShareLink(@PathVariable("id") Long id,
                                               @RequestParam(value = "expireHours", required = false) Integer expireHours) {
        return ResponseUtil.success(knowledgeBaseManageService.createShareLink(id, expireHours), "分享链接生成成功");
    }

    @GetMapping("/share/{token}")
    public Map<String, Object> getSharedArticle(@PathVariable("token") String token) {
        return ResponseUtil.success(knowledgeBaseManageService.getSharedArticle(token), "查询成功");
    }
}
