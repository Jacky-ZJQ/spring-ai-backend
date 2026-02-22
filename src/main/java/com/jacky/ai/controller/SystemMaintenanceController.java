package com.jacky.ai.controller;

import com.jacky.ai.service.SystemMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运维维护接口：
 * - 手动释放运行时内存
 * - 手动触发一次过期文件清理
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/system")
public class SystemMaintenanceController {

    private final SystemMaintenanceService systemMaintenanceService;

    @PostMapping("/release-memory")
    public Map<String, Object> releaseMemory(@RequestParam(value = "forceGc", defaultValue = "true") boolean forceGc,
                                             @RequestParam(value = "cleanupFiles", defaultValue = "false") boolean cleanupFiles) {
        SystemMaintenanceService.MemoryReleaseResult releaseResult = systemMaintenanceService.releaseRuntimeMemory(forceGc);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", 1);
        response.put("msg", "内存释放完成");
        response.put("beforeUsedMb", releaseResult.beforeUsedMb());
        response.put("afterUsedMb", releaseResult.afterUsedMb());
        response.put("clearedChatSessions", releaseResult.clearedChatSessions());
        response.put("clearedHistorySessions", releaseResult.clearedHistorySessions());
        response.put("clearedQuotaUsers", releaseResult.clearedQuotaUsers());
        response.put("gcInvoked", releaseResult.gcInvoked());
        response.put("time", LocalDateTime.now().toString());

        if (cleanupFiles) {
            SystemMaintenanceService.CleanupResult cleanupResult = systemMaintenanceService.cleanupExpiredFiles();
            response.put("deletedPdfFiles", cleanupResult.deletedPdfFiles());
            response.put("deletedImageFiles", cleanupResult.deletedImageFiles());
        }
        return response;
    }

    @PostMapping("/cleanup-files")
    public Map<String, Object> cleanupFiles() {
        SystemMaintenanceService.CleanupResult cleanupResult = systemMaintenanceService.cleanupExpiredFiles();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", 1);
        response.put("msg", "过期文件清理完成");
        response.put("deletedPdfFiles", cleanupResult.deletedPdfFiles());
        response.put("deletedImageFiles", cleanupResult.deletedImageFiles());
        response.put("time", LocalDateTime.now().toString());
        return response;
    }
}

