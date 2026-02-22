package com.jacky.ai.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPdfFileRepository implements FileRepository {

    private static final Path STORAGE_DIR = Path.of("storage", "pdf");

    private final VectorStore vectorStore;

    // 会话id 与 文件名的对应关系，方便查询会话历史时重新加载文件
    private final Properties chatFiles = new Properties();

    @Override
    public boolean save(String chatId, Resource resource) {
        String originalFilename = Objects.requireNonNullElse(resource.getFilename(), "uploaded.pdf");
        String safeFilename = chatId + "_" + sanitizeFilename(originalFilename);
        Path target = STORAGE_DIR.resolve(safeFilename);
        try {
            Files.createDirectories(STORAGE_DIR);
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            // 保存绝对路径，避免工作目录变化导致读取错误
            chatFiles.put(chatId, target.toAbsolutePath().toString());
            return true;
        } catch (IOException e) {
            log.error("Failed to save PDF resource.", e);
            return false;
        }
    }

    @Override
    public Resource getFile(String chatId) {
        String filepath = chatFiles.getProperty(chatId, "");
        return new FileSystemResource(filepath);
    }

    @PostConstruct
    private void init() {
        FileSystemResource pdfResource = new FileSystemResource("chat-pdf.properties");
        if (pdfResource.exists()) {
            try {
                chatFiles.load(new BufferedReader(new InputStreamReader(pdfResource.getInputStream(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                log.warn("Failed to load chat-pdf.properties, continue with empty mapping.", e);
            }
        }
        FileSystemResource vectorResource = new FileSystemResource("chat-pdf.json");
        if (vectorResource.exists()) {
            try {
                // 0 字节文件会触发 Jackson EOF 异常，这里直接跳过加载。
                if (vectorResource.contentLength() <= 0) {
                    log.warn("chat-pdf.json is empty, skip vector store loading.");
                    return;
                }
                SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
                simpleVectorStore.load(vectorResource);
            } catch (Exception e) {
                // 历史向量索引损坏不应阻断服务启动，允许后续上传 PDF 后重新建立索引。
                log.warn("Failed to load chat-pdf.json, continue with empty vector store.", e);
            }
        }
    }

    @PreDestroy
    private void persistent() {
        try {
            chatFiles.store(new FileWriter("chat-pdf.properties"), LocalDateTime.now().toString());
            SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
            simpleVectorStore.save(new File("chat-pdf.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 清理超期 PDF 文件（按文件最后修改时间判断）并同步清理映射关系。
     *
     * @param maxAgeMillis 允许保留的最大文件年龄（毫秒）
     * @return 删除文件数量
     */
    public synchronized int cleanupExpired(long maxAgeMillis) {
        if (maxAgeMillis <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int deletedFiles = 0;
        boolean mappingChanged = false;

        // 1) 清理“映射存在但文件缺失/过期”的记录
        Set<Path> mappedPaths = new HashSet<>();
        Iterator<Map.Entry<Object, Object>> iterator = chatFiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            String pathValue = String.valueOf(entry.getValue());
            Path filePath = Path.of(pathValue).toAbsolutePath().normalize();
            if (!Files.exists(filePath)) {
                iterator.remove();
                mappingChanged = true;
                continue;
            }
            if (isExpired(filePath, now, maxAgeMillis)) {
                try {
                    Files.deleteIfExists(filePath);
                    deletedFiles++;
                } catch (IOException e) {
                    log.warn("Failed to delete expired pdf file: {}", filePath, e);
                }
                iterator.remove();
                mappingChanged = true;
                continue;
            }
            mappedPaths.add(filePath);
        }

        // 2) 清理“没有映射”的孤儿文件，避免磁盘长期累积
        if (Files.exists(STORAGE_DIR)) {
            try (Stream<Path> files = Files.walk(STORAGE_DIR)) {
                for (Path filePath : files.filter(Files::isRegularFile).toList()) {
                    Path absolutePath = filePath.toAbsolutePath().normalize();
                    if (mappedPaths.contains(absolutePath)) {
                        continue;
                    }
                    if (!isExpired(absolutePath, now, maxAgeMillis)) {
                        continue;
                    }
                    try {
                        Files.deleteIfExists(absolutePath);
                        deletedFiles++;
                    } catch (IOException e) {
                        log.warn("Failed to delete orphan pdf file: {}", absolutePath, e);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to scan pdf storage for cleanup.", e);
            }
        }

        if (mappingChanged) {
            persistMappingOnly();
        }
        return deletedFiles;
    }

    private boolean isExpired(Path filePath, long now, long maxAgeMillis) {
        try {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            return now - lastModified > maxAgeMillis;
        } catch (IOException e) {
            log.warn("Failed to get file lastModified, treat as expired: {}", filePath, e);
            return true;
        }
    }

    private void persistMappingOnly() {
        try {
            chatFiles.store(new FileWriter("chat-pdf.properties"), LocalDateTime.now().toString());
        } catch (IOException e) {
            log.warn("Failed to persist chat-pdf.properties during cleanup.", e);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
