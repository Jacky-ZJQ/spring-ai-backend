package com.jacky.ai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacky.ai.entity.ChatImageMeta;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 本地文件系统实现，保存聊天上传图片，支持刷新后按历史轮次恢复。
 */
@Slf4j
@Component
public class LocalChatImageRepository implements ChatImageRepository {

    private static final Path STORAGE_DIR = Path.of("storage", "chat-images");

    private static final Path META_FILE = STORAGE_DIR.resolve("meta.json");

    /**
     * chatId -> (userTurn -> image metas)
     */
    private final Map<String, Map<Integer, List<ChatImageMeta>>> chatImageStore = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public LocalChatImageRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void init() {
        try {
            Files.createDirectories(STORAGE_DIR);
            if (!Files.exists(META_FILE)) {
                return;
            }
            Map<String, Map<Integer, List<ChatImageMeta>>> loaded = objectMapper.readValue(
                    META_FILE.toFile(),
                    new TypeReference<>() {
                    }
            );
            if (loaded == null || loaded.isEmpty()) {
                return;
            }
            // 转成并发结构，避免运行期读写时被 JSON 反序列化出的普通集合影响线程安全。
            loaded.forEach((chatId, turnMap) -> {
                Map<Integer, List<ChatImageMeta>> storedTurnMap = new ConcurrentHashMap<>();
                if (turnMap != null) {
                    turnMap.forEach((turn, metas) ->
                            storedTurnMap.put(turn, new CopyOnWriteArrayList<>(Objects.requireNonNullElse(metas, List.of())))
                    );
                }
                chatImageStore.put(chatId, storedTurnMap);
            });
        } catch (Exception e) {
            log.error("Failed to load chat image metadata: {}", META_FILE.toAbsolutePath(), e);
        }
    }

    @Override
    public synchronized List<ChatImageMeta> save(String chatId, int userTurn, List<MultipartFile> files) {
        if (!StringUtils.hasText(chatId) || userTurn <= 0 || files == null || files.isEmpty()) {
            return List.of();
        }
        Path chatDir = STORAGE_DIR.resolve(sanitizePathSegment(chatId));
        try {
            Files.createDirectories(chatDir);
        } catch (IOException e) {
            log.error("Failed to create chat image directory for chatId={}", chatId, e);
            return List.of();
        }

        List<ChatImageMeta> savedMetas = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = normalizeContentType(file.getContentType());
            if (!contentType.startsWith("image/")) {
                // 历史图片恢复仅关注图片，音频/视频不进入该仓储。
                continue;
            }
            String originalName = sanitizeDisplayName(file.getOriginalFilename());
            String storedName = buildStoredName(originalName, contentType);
            Path target = chatDir.resolve(storedName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                savedMetas.add(new ChatImageMeta(originalName, storedName, contentType, file.getSize()));
            } catch (IOException e) {
                log.error("Failed to save chat image file: chatId={}, file={}", chatId, originalName, e);
            }
        }

        if (savedMetas.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<ChatImageMeta>> turnMap = chatImageStore.computeIfAbsent(chatId, key -> new ConcurrentHashMap<>());
        List<ChatImageMeta> turnImages = turnMap.computeIfAbsent(userTurn, key -> new CopyOnWriteArrayList<>());
        turnImages.addAll(savedMetas);
        // 单机内存方案下每次写入都落盘，保证重启后还能恢复历史图片。
        persistMeta();
        return List.copyOf(savedMetas);
    }

    @Override
    public Map<Integer, List<ChatImageMeta>> getByChatId(String chatId) {
        Map<Integer, List<ChatImageMeta>> turnMap = chatImageStore.get(chatId);
        if (turnMap == null || turnMap.isEmpty()) {
            return Map.of();
        }
        // 返回副本，避免上层误改底层存储。
        return turnMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Override
    public Resource getFile(String chatId, String storedName) {
        if (!StringUtils.hasText(chatId) || !isSafeStoredName(storedName)) {
            return new FileSystemResource("");
        }
        Path filePath = STORAGE_DIR.resolve(sanitizePathSegment(chatId)).resolve(storedName);
        return new FileSystemResource(filePath.toAbsolutePath());
    }

    @Override
    public Optional<ChatImageMeta> findMeta(String chatId, String storedName) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(storedName)) {
            return Optional.empty();
        }
        Map<Integer, List<ChatImageMeta>> turnMap = chatImageStore.get(chatId);
        if (turnMap == null || turnMap.isEmpty()) {
            return Optional.empty();
        }
        return turnMap.values().stream()
                .flatMap(List::stream)
                .filter(meta -> storedName.equals(meta.getStoredName()))
                .findFirst();
    }

    private synchronized void persistMeta() {
        try {
            Files.createDirectories(STORAGE_DIR);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(META_FILE.toFile(), chatImageStore);
        } catch (Exception e) {
            log.error("Failed to persist chat image metadata: {}", META_FILE.toAbsolutePath(), e);
        }
    }

    private String normalizeContentType(String contentType) {
        return Objects.requireNonNullElse(contentType, "application/octet-stream").toLowerCase(Locale.ROOT);
    }

    private String sanitizePathSegment(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String sanitizeDisplayName(String originalName) {
        String safeName = StringUtils.hasText(originalName) ? originalName : "image";
        return safeName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String buildStoredName(String originalName, String contentType) {
        String extension = resolveExtension(originalName, contentType);
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return System.currentTimeMillis() + "_" + randomPart + extension;
    }

    private String resolveExtension(String originalName, String contentType) {
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
            return originalName.substring(dotIndex);
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/svg+xml" -> ".svg";
            default -> ".img";
        };
    }

    private boolean isSafeStoredName(String storedName) {
        // 仅允许仓储生成的纯文件名，阻止 "../" 之类的路径穿越。
        return !storedName.contains("..") && !storedName.contains("/") && !storedName.contains("\\");
    }
}
