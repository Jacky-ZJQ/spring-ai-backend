package com.jacky.ai.service;

import com.jacky.ai.entity.dto.knowledge.request.KnowledgeArticleQueryRequest;
import com.jacky.ai.entity.dto.knowledge.request.KnowledgeArticleSaveRequest;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeArticleDetail;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeArticleListItem;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeCategoryItem;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgePageResult;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeShareResult;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeTagItem;

import java.util.List;

public interface KnowledgeBaseManageService {

    KnowledgePageResult<KnowledgeArticleListItem> listArticles(KnowledgeArticleQueryRequest request);

    KnowledgeArticleDetail getArticleDetail(Long id);

    KnowledgeArticleDetail createArticle(KnowledgeArticleSaveRequest request);

    KnowledgeArticleDetail updateArticle(Long id, KnowledgeArticleSaveRequest request);

    void deleteArticle(Long id);

    KnowledgeArticleDetail publishArticle(Long id);

    KnowledgeArticleDetail unpublishArticle(Long id);

    KnowledgeArticleDetail likeArticle(Long id);

    List<KnowledgeCategoryItem> listCategories();

    List<KnowledgeTagItem> listTags();

    KnowledgeShareResult createShareLink(Long articleId, Integer expireHours);

    KnowledgeArticleDetail getSharedArticle(String token);
}
