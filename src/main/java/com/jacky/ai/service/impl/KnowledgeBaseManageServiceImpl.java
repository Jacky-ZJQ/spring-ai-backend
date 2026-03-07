package com.jacky.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jacky.ai.entity.dto.knowledge.request.KnowledgeArticleQueryRequest;
import com.jacky.ai.entity.dto.knowledge.request.KnowledgeArticleSaveRequest;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeArticleDetail;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeArticleListItem;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeCategoryItem;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgePageResult;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeShareResult;
import com.jacky.ai.entity.dto.knowledge.response.KnowledgeTagItem;
import com.jacky.ai.entity.po.KnowledgeArticle;
import com.jacky.ai.entity.po.KnowledgeArticleTag;
import com.jacky.ai.entity.po.KnowledgeCategory;
import com.jacky.ai.entity.po.KnowledgeShareLink;
import com.jacky.ai.entity.po.KnowledgeTag;
import com.jacky.ai.mapper.KnowledgeArticleMapper;
import com.jacky.ai.mapper.KnowledgeArticleTagMapper;
import com.jacky.ai.mapper.KnowledgeCategoryMapper;
import com.jacky.ai.mapper.KnowledgeShareLinkMapper;
import com.jacky.ai.mapper.KnowledgeTagMapper;
import com.jacky.ai.service.KnowledgeBaseManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseManageServiceImpl implements KnowledgeBaseManageService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ALL = "ALL";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final String SHARE_ACTIVE = "ACTIVE";
    private static final String SHARE_DISABLED = "DISABLED";
    private static final Set<String> ALLOWED_TYPES = Set.of("PROMPT", "WORKFLOW", "CASE", "NOTE");
    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED);
    private static final Set<String> ALLOWED_VISIBILITIES = Set.of(VISIBILITY_PUBLIC, VISIBILITY_PRIVATE);

    private final KnowledgeArticleMapper articleMapper;
    private final KnowledgeCategoryMapper categoryMapper;
    private final KnowledgeTagMapper tagMapper;
    private final KnowledgeArticleTagMapper articleTagMapper;
    private final KnowledgeShareLinkMapper shareLinkMapper;

    @Override
    public KnowledgePageResult<KnowledgeArticleListItem> listArticles(KnowledgeArticleQueryRequest request) {
        KnowledgeArticleQueryRequest safeRequest = request == null ? new KnowledgeArticleQueryRequest() : request;
        int pageNo = normalizePageNo(safeRequest.getPageNo());
        int pageSize = normalizePageSize(safeRequest.getPageSize());
        String keyword = trimToNull(safeRequest.getQ());
        String type = StringUtils.hasText(safeRequest.getType()) ? normalizeType(safeRequest.getType()) : null;
        String status = normalizeStatusForList(safeRequest.getStatus());

        List<Long> tagMatchedArticleIds = null;
        if (safeRequest.getTagId() != null) {
            List<KnowledgeArticleTag> relations = articleTagMapper.selectList(
                    Wrappers.lambdaQuery(KnowledgeArticleTag.class)
                            .eq(KnowledgeArticleTag::getTagId, safeRequest.getTagId()));
            if (relations.isEmpty()) {
                return KnowledgePageResult.of(List.of(), 0L, pageNo, pageSize);
            }
            tagMatchedArticleIds = relations.stream()
                    .map(KnowledgeArticleTag::getArticleId)
                    .distinct()
                    .toList();
        }

        LambdaQueryWrapper<KnowledgeArticle> query = Wrappers.lambdaQuery(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getDeleted, 0)
                .eq(StringUtils.hasText(status), KnowledgeArticle::getStatus, status)
                .eq(StringUtils.hasText(type), KnowledgeArticle::getType, type)
                .eq(safeRequest.getCategoryId() != null, KnowledgeArticle::getCategoryId, safeRequest.getCategoryId())
                .in(tagMatchedArticleIds != null, KnowledgeArticle::getId, tagMatchedArticleIds)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(KnowledgeArticle::getTitle, keyword)
                        .or()
                        .like(KnowledgeArticle::getSummary, keyword)
                        .or()
                        .like(KnowledgeArticle::getContentMd, keyword));

        long total = safeCount(articleMapper.selectCount(query));
        if (total == 0) {
            return KnowledgePageResult.of(List.of(), 0L, pageNo, pageSize);
        }

        applySort(query, safeRequest.getSort());
        int offset = (pageNo - 1) * pageSize;
        query.last("limit " + offset + "," + pageSize);

        List<KnowledgeArticle> articles = articleMapper.selectList(query);
        if (articles.isEmpty()) {
            return KnowledgePageResult.of(List.of(), total, pageNo, pageSize);
        }

        Map<Long, KnowledgeCategory> categoryMap = loadCategoryMap(
                articles.stream().map(KnowledgeArticle::getCategoryId).toList());
        Map<Long, List<KnowledgeTagItem>> articleTags = loadArticleTagMap(
                articles.stream().map(KnowledgeArticle::getId).toList());

        List<KnowledgeArticleListItem> items = new ArrayList<>(articles.size());
        for (KnowledgeArticle article : articles) {
            KnowledgeArticleListItem item = new KnowledgeArticleListItem();
            item.setId(article.getId());
            item.setTitle(article.getTitle());
            item.setSummary(article.getSummary());
            item.setType(article.getType());
            item.setStatus(article.getStatus());
            item.setVisibility(article.getVisibility());
            item.setCategoryId(article.getCategoryId());
            item.setCategoryName(resolveCategoryName(categoryMap, article.getCategoryId()));
            item.setCoverUrl(article.getCoverUrl());
            item.setViewCount(defaultInt(article.getViewCount()));
            item.setLikeCount(defaultInt(article.getLikeCount()));
            item.setFavoriteCount(defaultInt(article.getFavoriteCount()));
            item.setPublishedAt(article.getPublishedAt());
            item.setUpdatedAt(article.getUpdatedAt());
            item.setTags(articleTags.getOrDefault(article.getId(), List.of()));
            items.add(item);
        }
        return KnowledgePageResult.of(items, total, pageNo, pageSize);
    }

    @Override
    public KnowledgeArticleDetail getArticleDetail(Long id) {
        KnowledgeArticle article = requireArticle(id);
        incrementArticleViewCount(id);
        article.setViewCount(defaultInt(article.getViewCount()) + 1);
        return buildArticleDetail(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleDetail createArticle(KnowledgeArticleSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        String title = requireText(request.getTitle(), "标题不能为空");
        String type = normalizeType(request.getType());
        String contentMd = requireText(request.getContentMd(), "内容不能为空");
        Long categoryId = validateCategory(request.getCategoryId());
        List<Long> tagIds = validateTagIds(request.getTagIds());
        String status = normalizeStatus(request.getStatus(), STATUS_DRAFT);
        String visibility = normalizeVisibility(request.getVisibility(), VISIBILITY_PUBLIC);
        String operator = resolveOperator(request.getCreatedBy(), request.getUpdatedBy(), "portal");

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(title);
        article.setSummary(buildSummary(request.getSummary(), contentMd));
        article.setType(type);
        article.setCategoryId(categoryId);
        article.setContentMd(contentMd.trim());
        article.setExtraJson(trimToNull(request.getExtraJson()));
        article.setCoverUrl(trimToNull(request.getCoverUrl()));
        article.setVisibility(visibility);
        article.setStatus(status);
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setFavoriteCount(0);
        article.setCreatedBy(operator);
        article.setUpdatedBy(operator);
        article.setPublishedAt(STATUS_PUBLISHED.equals(status) ? now : null);
        article.setDeleted(0);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);

        articleMapper.insert(article);
        replaceTags(article.getId(), tagIds);
        return buildArticleDetail(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleDetail updateArticle(Long id, KnowledgeArticleSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        KnowledgeArticle article = requireArticle(id);
        LocalDateTime now = LocalDateTime.now();

        String title = StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : article.getTitle();
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("标题不能为空");
        }
        String contentMd = StringUtils.hasText(request.getContentMd()) ? request.getContentMd().trim() : article.getContentMd();
        if (!StringUtils.hasText(contentMd)) {
            throw new IllegalArgumentException("内容不能为空");
        }

        article.setTitle(title);
        article.setSummary(request.getSummary() == null ? article.getSummary() : buildSummary(request.getSummary(), contentMd));
        article.setType(StringUtils.hasText(request.getType()) ? normalizeType(request.getType()) : article.getType());
        article.setCategoryId(request.getCategoryId() == null ? article.getCategoryId() : validateCategory(request.getCategoryId()));
        article.setContentMd(contentMd);
        if (request.getExtraJson() != null) {
            article.setExtraJson(trimToNull(request.getExtraJson()));
        }
        if (request.getCoverUrl() != null) {
            article.setCoverUrl(trimToNull(request.getCoverUrl()));
        }

        String status = StringUtils.hasText(request.getStatus())
                ? normalizeStatus(request.getStatus(), article.getStatus())
                : article.getStatus();
        String visibility = StringUtils.hasText(request.getVisibility())
                ? normalizeVisibility(request.getVisibility(), article.getVisibility())
                : article.getVisibility();
        article.setStatus(status);
        article.setVisibility(visibility);
        article.setUpdatedBy(resolveOperator(request.getUpdatedBy(), request.getCreatedBy(), article.getUpdatedBy()));
        article.setUpdatedAt(now);
        if (STATUS_PUBLISHED.equals(status) && article.getPublishedAt() == null) {
            article.setPublishedAt(now);
        }
        if (!STATUS_PUBLISHED.equals(status)) {
            article.setPublishedAt(null);
        }

        articleMapper.updateById(article);
        if (request.getTagIds() != null) {
            replaceTags(article.getId(), validateTagIds(request.getTagIds()));
        }
        return buildArticleDetail(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id) {
        KnowledgeArticle article = requireArticle(id);
        article.setDeleted(1);
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);
        shareLinkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeShareLink.class)
                .eq(KnowledgeShareLink::getArticleId, id)
                .set(KnowledgeShareLink::getStatus, SHARE_DISABLED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleDetail publishArticle(Long id) {
        KnowledgeArticle article = requireArticle(id);
        LocalDateTime now = LocalDateTime.now();
        article.setStatus(STATUS_PUBLISHED);
        article.setPublishedAt(now);
        article.setUpdatedAt(now);
        articleMapper.updateById(article);
        return buildArticleDetail(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleDetail unpublishArticle(Long id) {
        KnowledgeArticle article = requireArticle(id);
        article.setStatus(STATUS_DRAFT);
        article.setPublishedAt(null);
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);
        return buildArticleDetail(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleDetail likeArticle(Long id) {
        KnowledgeArticle article = requireArticle(id);
        incrementArticleLikeCount(id);
        article.setLikeCount(defaultInt(article.getLikeCount()) + 1);
        return buildArticleDetail(article);
    }

    @Override
    public List<KnowledgeCategoryItem> listCategories() {
        return categoryMapper.selectList(Wrappers.lambdaQuery(KnowledgeCategory.class)
                        .eq(KnowledgeCategory::getEnabled, 1)
                        .orderByAsc(KnowledgeCategory::getSortOrder)
                        .orderByAsc(KnowledgeCategory::getId))
                .stream()
                .map(this::toCategoryItem)
                .toList();
    }

    @Override
    public List<KnowledgeTagItem> listTags() {
        return tagMapper.selectList(Wrappers.lambdaQuery(KnowledgeTag.class)
                        .eq(KnowledgeTag::getEnabled, 1)
                        .orderByAsc(KnowledgeTag::getSortOrder)
                        .orderByAsc(KnowledgeTag::getId))
                .stream()
                .map(this::toTagItem)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeShareResult createShareLink(Long articleId, Integer expireHours) {
        KnowledgeArticle article = requireArticle(articleId);
        if (!STATUS_PUBLISHED.equals(article.getStatus())) {
            throw new IllegalStateException("仅已发布文章支持生成分享链接");
        }

        KnowledgeShareLink shareLink = new KnowledgeShareLink();
        shareLink.setArticleId(articleId);
        shareLink.setShareToken(generateShareToken());
        shareLink.setExpireAt(buildExpireAt(expireHours));
        shareLink.setStatus(SHARE_ACTIVE);
        shareLink.setViewCount(0);
        shareLink.setCreatedBy(resolveOperator(article.getUpdatedBy(), article.getCreatedBy(), "portal"));
        shareLink.setCreatedAt(LocalDateTime.now());
        shareLinkMapper.insert(shareLink);

        KnowledgeShareResult result = new KnowledgeShareResult();
        result.setArticleId(articleId);
        result.setShareToken(shareLink.getShareToken());
        result.setSharePath("/ai/knowledge/share/" + shareLink.getShareToken());
        result.setExpireAt(shareLink.getExpireAt());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleDetail getSharedArticle(String token) {
        String shareToken = requireText(token, "分享令牌不能为空");
        KnowledgeShareLink shareLink = shareLinkMapper.selectOne(Wrappers.lambdaQuery(KnowledgeShareLink.class)
                .eq(KnowledgeShareLink::getShareToken, shareToken)
                .eq(KnowledgeShareLink::getStatus, SHARE_ACTIVE)
                .last("limit 1"));
        if (shareLink == null) {
            throw new IllegalArgumentException("分享链接不存在");
        }
        if (shareLink.getExpireAt() != null && shareLink.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("分享链接已过期");
        }

        KnowledgeArticle article = requireArticle(shareLink.getArticleId());
        if (!STATUS_PUBLISHED.equals(article.getStatus())) {
            throw new IllegalStateException("文章未发布，无法访问分享内容");
        }

        incrementShareViewCount(shareLink.getId());
        incrementArticleViewCount(article.getId());
        article.setViewCount(defaultInt(article.getViewCount()) + 1);
        return buildArticleDetail(article);
    }

    private KnowledgeArticle requireArticle(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("文章ID不能为空");
        }
        KnowledgeArticle article = articleMapper.selectOne(Wrappers.lambdaQuery(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getId, id)
                .eq(KnowledgeArticle::getDeleted, 0)
                .last("limit 1"));
        if (article == null) {
            throw new IllegalArgumentException("知识卡片不存在");
        }
        return article;
    }

    private Long validateCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        KnowledgeCategory category = categoryMapper.selectOne(Wrappers.lambdaQuery(KnowledgeCategory.class)
                .eq(KnowledgeCategory::getId, categoryId)
                .eq(KnowledgeCategory::getEnabled, 1)
                .last("limit 1"));
        if (category == null) {
            throw new IllegalArgumentException("分类不存在或已禁用");
        }
        return categoryId;
    }

    private List<Long> validateTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(tagIds));
        List<KnowledgeTag> tags = tagMapper.selectList(Wrappers.lambdaQuery(KnowledgeTag.class)
                .in(KnowledgeTag::getId, uniqueIds)
                .eq(KnowledgeTag::getEnabled, 1));
        if (tags.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("标签不存在或已禁用");
        }
        return uniqueIds;
    }

    private void replaceTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(Wrappers.lambdaQuery(KnowledgeArticleTag.class)
                .eq(KnowledgeArticleTag::getArticleId, articleId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            KnowledgeArticleTag relation = new KnowledgeArticleTag();
            relation.setArticleId(articleId);
            relation.setTagId(tagId);
            articleTagMapper.insert(relation);
        }
    }

    private KnowledgeArticleDetail buildArticleDetail(KnowledgeArticle article) {
        Map<Long, KnowledgeCategory> categoryMap = loadCategoryMap(Collections.singletonList(article.getCategoryId()));
        Map<Long, List<KnowledgeTagItem>> articleTagMap = loadArticleTagMap(List.of(article.getId()));

        KnowledgeArticleDetail detail = new KnowledgeArticleDetail();
        detail.setId(article.getId());
        detail.setTitle(article.getTitle());
        detail.setSummary(article.getSummary());
        detail.setType(article.getType());
        detail.setStatus(article.getStatus());
        detail.setVisibility(article.getVisibility());
        detail.setCategoryId(article.getCategoryId());
        detail.setCategoryName(resolveCategoryName(categoryMap, article.getCategoryId()));
        detail.setContentMd(article.getContentMd());
        detail.setExtraJson(article.getExtraJson());
        detail.setCoverUrl(article.getCoverUrl());
        detail.setViewCount(defaultInt(article.getViewCount()));
        detail.setLikeCount(defaultInt(article.getLikeCount()));
        detail.setFavoriteCount(defaultInt(article.getFavoriteCount()));
        detail.setCreatedBy(article.getCreatedBy());
        detail.setUpdatedBy(article.getUpdatedBy());
        detail.setPublishedAt(article.getPublishedAt());
        detail.setCreatedAt(article.getCreatedAt());
        detail.setUpdatedAt(article.getUpdatedAt());
        detail.setTags(articleTagMap.getOrDefault(article.getId(), List.of()));
        return detail;
    }

    private Map<Long, KnowledgeCategory> loadCategoryMap(Collection<Long> categoryIds) {
        List<Long> ids = categoryIds == null ? List.of() : categoryIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectList(Wrappers.lambdaQuery(KnowledgeCategory.class)
                        .in(KnowledgeCategory::getId, ids))
                .stream()
                .collect(Collectors.toMap(KnowledgeCategory::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, List<KnowledgeTagItem>> loadArticleTagMap(Collection<Long> articleIds) {
        List<Long> ids = articleIds == null ? List.of() : articleIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeArticleTag> relations = articleTagMapper.selectList(Wrappers.lambdaQuery(KnowledgeArticleTag.class)
                .in(KnowledgeArticleTag::getArticleId, ids)
                .orderByAsc(KnowledgeArticleTag::getId));
        if (relations.isEmpty()) {
            return Map.of();
        }

        List<Long> tagIds = relations.stream().map(KnowledgeArticleTag::getTagId).distinct().toList();
        Map<Long, KnowledgeTag> tagMap = tagMapper.selectList(Wrappers.lambdaQuery(KnowledgeTag.class)
                        .in(KnowledgeTag::getId, tagIds))
                .stream()
                .collect(Collectors.toMap(KnowledgeTag::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<KnowledgeTagItem>> articleTagMap = new LinkedHashMap<>();
        for (KnowledgeArticleTag relation : relations) {
            KnowledgeTag tag = tagMap.get(relation.getTagId());
            if (tag == null) {
                continue;
            }
            articleTagMap.computeIfAbsent(relation.getArticleId(), key -> new ArrayList<>()).add(toTagItem(tag));
        }
        return articleTagMap;
    }

    private KnowledgeCategoryItem toCategoryItem(KnowledgeCategory category) {
        KnowledgeCategoryItem item = new KnowledgeCategoryItem();
        item.setId(category.getId());
        item.setName(category.getName());
        item.setCode(category.getCode());
        item.setSortOrder(category.getSortOrder());
        return item;
    }

    private KnowledgeTagItem toTagItem(KnowledgeTag tag) {
        KnowledgeTagItem item = new KnowledgeTagItem();
        item.setId(tag.getId());
        item.setName(tag.getName());
        return item;
    }

    private String resolveCategoryName(Map<Long, KnowledgeCategory> categoryMap, Long categoryId) {
        if (categoryId == null || categoryMap.isEmpty()) {
            return null;
        }
        KnowledgeCategory category = categoryMap.get(categoryId);
        return category == null ? null : category.getName();
    }

    private void applySort(LambdaQueryWrapper<KnowledgeArticle> query, String sort) {
        String normalizedSort = trimToNull(sort);
        if ("popular".equalsIgnoreCase(normalizedSort) || "hot".equalsIgnoreCase(normalizedSort)) {
            query.orderByDesc(KnowledgeArticle::getViewCount, KnowledgeArticle::getLikeCount, KnowledgeArticle::getId);
            return;
        }
        if ("oldest".equalsIgnoreCase(normalizedSort)) {
            query.orderByAsc(KnowledgeArticle::getId);
            return;
        }
        if ("updated".equalsIgnoreCase(normalizedSort)) {
            query.orderByDesc(KnowledgeArticle::getUpdatedAt, KnowledgeArticle::getId);
            return;
        }
        query.orderByDesc(KnowledgeArticle::getPublishedAt, KnowledgeArticle::getUpdatedAt, KnowledgeArticle::getId);
    }

    private void incrementArticleViewCount(Long articleId) {
        articleMapper.update(null, Wrappers.lambdaUpdate(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getId, articleId)
                .setSql("view_count = view_count + 1"));
    }

    private void incrementArticleLikeCount(Long articleId) {
        articleMapper.update(null, Wrappers.lambdaUpdate(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getId, articleId)
                .setSql("like_count = like_count + 1"));
    }

    private void incrementShareViewCount(Long shareId) {
        shareLinkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeShareLink.class)
                .eq(KnowledgeShareLink::getId, shareId)
                .setSql("view_count = view_count + 1"));
    }

    private LocalDateTime buildExpireAt(Integer expireHours) {
        int safeHours = expireHours == null ? 168 : Math.max(1, Math.min(expireHours, 720));
        return LocalDateTime.now().plusHours(safeHours);
    }

    private String generateShareToken() {
        for (int i = 0; i < 5; i++) {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            Long count = shareLinkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeShareLink.class)
                    .eq(KnowledgeShareLink::getShareToken, token));
            if (safeCount(count) == 0) {
                return token;
            }
        }
        throw new IllegalStateException("分享链接生成失败，请稍后重试");
    }

    private String normalizeType(String type) {
        String normalized = requireText(type, "类型不能为空").toUpperCase();
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("type 仅支持 PROMPT/WORKFLOW/CASE/NOTE");
        }
        return normalized;
    }

    private String normalizeStatus(String status, String defaultStatus) {
        String fallback = StringUtils.hasText(defaultStatus) ? defaultStatus.trim().toUpperCase() : STATUS_DRAFT;
        if (!StringUtils.hasText(status)) {
            return fallback;
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("status 仅支持 DRAFT/PUBLISHED");
        }
        return normalized;
    }

    private String normalizeStatusForList(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_PUBLISHED;
        }
        if (STATUS_ALL.equalsIgnoreCase(status.trim())) {
            return null;
        }
        return normalizeStatus(status, STATUS_PUBLISHED);
    }

    private String normalizeVisibility(String visibility, String defaultVisibility) {
        String fallback = StringUtils.hasText(defaultVisibility) ? defaultVisibility.trim().toUpperCase() : VISIBILITY_PUBLIC;
        if (!StringUtils.hasText(visibility)) {
            return fallback;
        }
        String normalized = visibility.trim().toUpperCase();
        if (!ALLOWED_VISIBILITIES.contains(normalized)) {
            throw new IllegalArgumentException("visibility 仅支持 PUBLIC/PRIVATE");
        }
        return normalized;
    }

    private String buildSummary(String summary, String contentMd) {
        String summaryText = trimToNull(summary);
        if (!StringUtils.hasText(summaryText)) {
            summaryText = trimToNull(contentMd);
        }
        if (!StringUtils.hasText(summaryText)) {
            return null;
        }
        return abbreviate(summaryText.replaceAll("\\s+", " "), 160);
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    private String resolveOperator(String primary, String secondary, String fallback) {
        String operator = trimToNull(primary);
        if (!StringUtils.hasText(operator)) {
            operator = trimToNull(secondary);
        }
        return StringUtils.hasText(operator) ? operator : fallback;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeCount(Long value) {
        return value == null ? 0L : value;
    }
}
