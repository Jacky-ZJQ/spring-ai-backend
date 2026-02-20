package com.jacky.ai.controller;

import com.jacky.ai.entity.vo.Result;
import com.jacky.ai.repository.ChatHistoryRepository;
import com.jacky.ai.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {

    private final FileRepository fileRepository;

    private final VectorStore vectorStore;

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatClient pdfOpenAiChatClient;

    /**
     * 1、PDF聊天
     * @param prompt 提示词
     * @param chatId 会话id
     * @return 响应流chatId
     */
    @RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")
    public Flux<String> chat(String prompt, String chatId) {
        chatHistoryRepository.save("pdf", chatId);
        Resource file = fileRepository.getFile(chatId);
        return pdfOpenAiChatClient
                .prompt(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '"+ file.getFilename() +"'"))
                .stream()
                .content();
    }


    /**
     * 2、文件上传
     * @param chatId 会话id
     * @param file 文件
     * @return 结果
     */
    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验文件是否为PDF格式
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                return Result.fail("只能上传PDF文件！");
            }
            // 2.保存文件
            boolean success = fileRepository.save(chatId, file.getResource());
            if(! success) {
                return Result.fail("保存文件失败！");
            }
            // 3.写入向量库
            Resource savedResource = fileRepository.getFile(chatId);
            if (!savedResource.exists()) {
                return Result.fail("保存文件失败！");
            }
            this.writeToVectorStore(savedResource);
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to upload PDF.", e);
            return Result.fail("上传文件失败！");
        }
    }

    /**
     * 3、文件下载
     * @param chatId 会话id
     * @return 文件流
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId") String chatId) throws IOException {
        // 1.读取文件
        Resource resource = fileRepository.getFile(chatId);


        System.out.println("=== PDF下载调试信息 ===");
        System.out.println("chatId: " + chatId);
        System.out.println("Resource: " + resource);
        System.out.println("Resource exists: " + resource.exists());
        System.out.println("Resource filename: " + resource.getFilename());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        // 2.尝试获取文件大小
        try {
            long fileSize = resource.contentLength();
            System.out.println("文件大小: " + fileSize + " bytes");

            if (fileSize == 0) {
                System.err.println("警告：文件大小为0！");

                // 尝试通过File对象获取大小
                File file = resource.getFile();
                System.out.println("File对象路径: " + file.getAbsolutePath());
                System.out.println("File对象大小: " + file.length() + " bytes");
                System.out.println("File对象是否存在: " + file.exists());
                System.out.println("File对象可读: " + file.canRead());
            }
        } catch (Exception e) {
            System.err.println("获取文件大小失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 2.文件名编码，写入响应头
        String filename = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        // 3.返回文件
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    private void writeToVectorStore(Resource resource) {
        // 1.创建PDF的读取器
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource, // 文件源
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1) // 每1页PDF作为一个Document
                        .build()
        );
        // 2.读取PDF文档，拆分为Document
        List<Document> documents = reader.read();
        // 3.写入向量库 TODO 量库是基于内存实现，是一个专门用来测试、教学用的库，每个企业用的向量库都不一样，后续需要安装向量库
        vectorStore.add(documents);
    }

}
