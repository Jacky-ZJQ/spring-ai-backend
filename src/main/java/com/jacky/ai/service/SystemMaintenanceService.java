package com.jacky.ai.service;

import com.jacky.ai.config.AiQuotaInterceptor;
import com.jacky.ai.repository.ChatHistoryRepository;
import com.jacky.ai.repository.LocalChatImageRepository;
import com.jacky.ai.repository.LocalPdfFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统维护服务：
 * 1) 定时清理过期 PDF / 聊天图片文件；
 * 2) 提供手动释放运行时内存能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMaintenanceService {

    private static final long ONE_MB = 1024L * 1024L;

    private final LocalPdfFileRepository localPdfFileRepository;

    private final LocalChatImageRepository localChatImageRepository;

    private final ChatMemory chatMemory;

    private final ChatHistoryRepository chatHistoryRepository;

    private final AiQuotaInterceptor aiQuotaInterceptor;

    @Value("${app.maintenance.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${app.maintenance.pdf-retention-hours:168}")
    private long pdfRetentionHours;

    @Value("${app.maintenance.image-retention-hours:168}")
    private long imageRetentionHours;

    @Value("${app.maintenance.chat-types:chat,service,pdf}")
    private String trackedChatTypes;

    /**
     * 每天固定时间执行过期文件清理，减少磁盘占用并避免长期积压。
     */
    @Scheduled(cron = "${app.maintenance.cleanup-cron:0 0 4 * * *}")
    public void scheduledCleanupExpiredFiles() {
        if (!cleanupEnabled) {
            return;
        }
        CleanupResult result = cleanupExpiredFiles();
        log.info("Scheduled cleanup completed, deletedPdfFiles={}, deletedImageFiles={}",
                result.deletedPdfFiles(), result.deletedImageFiles());
    }

    public CleanupResult cleanupExpiredFiles() {
        long pdfMaxAgeMillis = hoursToMillis(pdfRetentionHours);
        long imageMaxAgeMillis = hoursToMillis(imageRetentionHours);
        int deletedPdfFiles = localPdfFileRepository.cleanupExpired(pdfMaxAgeMillis);
        int deletedImageFiles = localChatImageRepository.cleanupExpired(imageMaxAgeMillis);
        return new CleanupResult(deletedPdfFiles, deletedImageFiles);
    }

    public MemoryReleaseResult releaseRuntimeMemory(boolean forceGc) {
        long beforeUsedMb = usedMemoryMb();

        Set<String> chatIds = collectTrackedChatIds();
        int clearedChatSessions = 0;
        for (String chatId : chatIds) {
            try {
                chatMemory.clear(chatId);
                clearedChatSessions++;
            } catch (Exception ex) {
                log.warn("Failed to clear chat memory for chatId={}", chatId, ex);
            }
        }

        int clearedHistorySessions = chatHistoryRepository.clearAll();
        int clearedQuotaUsers = aiQuotaInterceptor.clearAllCounters();

        if (forceGc) {
            System.gc();
        }
        long afterUsedMb = usedMemoryMb();

        return new MemoryReleaseResult(
                beforeUsedMb,
                afterUsedMb,
                clearedChatSessions,
                clearedHistorySessions,
                clearedQuotaUsers,
                forceGc
        );
    }

    private Set<String> collectTrackedChatIds() {
        LinkedHashSet<String> chatIds = new LinkedHashSet<>();
        for (String type : resolveTrackedChatTypes()) {
            chatIds.addAll(chatHistoryRepository.getChatIds(type));
        }
        return chatIds;
    }

    private List<String> resolveTrackedChatTypes() {
        return Arrays.stream(trackedChatTypes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private long hoursToMillis(long hours) {
        long safeHours = Math.max(1L, hours);
        return safeHours * 3600_000L;
    }

    private long usedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        return usedBytes / ONE_MB;
    }

    public record CleanupResult(int deletedPdfFiles, int deletedImageFiles) {
    }

    public record MemoryReleaseResult(long beforeUsedMb,
                                      long afterUsedMb,
                                      int clearedChatSessions,
                                      int clearedHistorySessions,
                                      int clearedQuotaUsers,
                                      boolean gcInvoked) {
    }
}

