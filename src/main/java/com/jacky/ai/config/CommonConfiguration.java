package com.jacky.ai.config;

import com.jacky.ai.constants.SystemConstants;
import com.jacky.ai.tools.CourseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.jacky.ai.constants.SystemConstants.CUSTOMER_SERVICE_SYSTEM;

/**
 * @author: Jacky.Z
 * @date: 2025/6/17 22:24
 * @description：
 * SpringAI基于AOP机制实现与大模型对话过程的增强、拦截、修改等功能。所有的增强通知都需要实现Advisor接口。
 * - SimpleLoggerAdvisor：日志记录的Advisor
 * - MessageChatMemoryAdvisor：会话记忆的Advisor
 * - QuestionAnswerAdvisor：实现RAG的Advisor
 */
@Configuration
public class CommonConfiguration {

    /**
     * 本地Ollama聊天客户端
     * @param model      Ollama聊天模型，本地部署：deepseek-r1:1.5b
     * @param chatMemory 聊天内存存储
     * @return 本地Ollama聊天客户端
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel model, ChatMemory chatMemory) {
        //会得到一个ChatClient.Builder工厂对象，利用它可以自由选择模型、添加各种自定义配置
        return ChatClient.builder(model)
                .defaultSystem("你是一个热心、可爱的星巴克客服智能助手，你的名字叫小星星，请以小星星的身份和语气回答问题。")// 设置默认的系统提示语
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 添加默认的日志记录的Advisor
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory)) // 会话记忆的Advisor
                .build();
    }

    /**
     * 云端OpenAI聊天客户端（多模态）
     * @param model      OpenAI聊天模型，云端部署：qwen-omni-turbo
     * @param chatMemory 聊天内存存储
     * @return 云端OpenAI聊天客户端
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model) // 创建ChatClient工厂实例
                .defaultOptions(ChatOptions.builder().model("qwen-omni-turbo").build())// 多模态
                .defaultSystem("你是一个热心、可爱的星巴克客服智能助手，你的名字叫小星星，请以小星星的身份和语气回答问题。")
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 添加默认的Advisor,记录日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build(); // 构建ChatClient实例

    }

    /**
     * TODO 创建内存存储，这里用的是InMemoryChatMemory，可以根据需要替换成其他的实现
     * 例如：`CassandraChatMemory`：会话保存在Cassandra数据库中（需要引入额外依赖，并且绑定了向量数据库，不够灵活）
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * 创建游戏聊天客户端，这里用的是OpenAIChatModel，可以根据需要替换成其他的实现
     *
     * @param model      openAI聊天模型
     * @param chatMemory 聊天内存存储
     * @return 游戏聊天客户端
     */
    @Bean
    public ChatClient gameOpenAiChatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(SystemConstants.GAME_SYSTEM_PROMPT)// 设置默认的系统提示语
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 添加默认的Advisor,记录Agent日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory)) // 设置内存存储
                .build();
    }


    /**
     * 创建客服聊天客户端，这里用的是OpenAIChatModel，可以根据需要替换成其他的实现
     * @param model      openAI聊天模型（这里用的是AlibabaOpenAiChatModel）
     * @param chatMemory 聊天内存存储
     * @param courseTools 课程工具类
     * @return 客服聊天客户端
     */
    @Bean
    public ChatClient serviceOpenAiChatClient(OpenAiChatModel model, ChatMemory chatMemory, CourseTools courseTools) {
        return ChatClient.builder(model)
                .defaultSystem(CUSTOMER_SERVICE_SYSTEM)// 设置默认的系统提示语
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 添加默认的Advisor,记录Agent日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory)) // 设置内存存储
                .defaultTools(courseTools) // 添加自定的工具类
                .build();
    }

// TODO: 兼容 阿里云百炼 无法使用stream API的问题
//    @Bean
//    public AlibabaOpenAiChatModel alibabaOpenAiChatModel(OpenAiConnectionProperties commonProperties, OpenAiChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<ChatModelObservationConvention> observationConvention) {
//        String baseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl() : commonProperties.getBaseUrl();
//        String apiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey() : commonProperties.getApiKey();
//        String projectId = StringUtils.hasText(chatProperties.getProjectId()) ? chatProperties.getProjectId() : commonProperties.getProjectId();
//        String organizationId = StringUtils.hasText(chatProperties.getOrganizationId()) ? chatProperties.getOrganizationId() : commonProperties.getOrganizationId();
//        Map<String, List<String>> connectionHeaders = new HashMap<>();
//        if (StringUtils.hasText(projectId)) {
//            connectionHeaders.put("OpenAI-Project", List.of(projectId));
//        }
//
//        if (StringUtils.hasText(organizationId)) {
//            connectionHeaders.put("OpenAI-Organization", List.of(organizationId));
//        }
//        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
//        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
//        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(new SimpleApiKey(apiKey)).headers(CollectionUtils.toMultiValueMap(connectionHeaders)).completionsPath(chatProperties.getCompletionsPath()).embeddingsPath("/v1/embeddings").restClientBuilder(restClientBuilder).webClientBuilder(webClientBuilder).responseErrorHandler(responseErrorHandler).build();
//        AlibabaOpenAiChatModel chatModel = AlibabaOpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(chatProperties.getOptions()).toolCallingManager(toolCallingManager).retryTemplate(retryTemplate).observationRegistry((ObservationRegistry)observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)).build();
//        Objects.requireNonNull(chatModel);
//        observationConvention.ifAvailable(chatModel::setObservationConvention);
//        return chatModel;
//    }


    /**
     * 创建OpenAI Embedding模型，这里用的是SimpleVectorStore，可以根据需要替换成其他的实现
     * @param embeddingModel openAI Embedding模型
     * @return
     */
    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * 创建PDF聊天客户端，这里用的是OpenAIChatModel，可以根据需要替换成其他的实现
     * @param model      openAI聊天模型（这里用的是AlibabaOpenAiChatModel）
     * @param chatMemory 聊天内存存储
     * @param vectorStore 向量库
     * @return PDF聊天客户端
     */
    @Bean
    public ChatClient pdfOpenAiChatClient(OpenAiChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient.builder(model)
                .defaultSystem("请根据提供的上下文回答问题，不要自己猜测。")
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory), // CHAT MEMORY
                        new SimpleLoggerAdvisor(),
                        new QuestionAnswerAdvisor(
                                vectorStore, // 向量库
                                SearchRequest.builder() // 向量检索的请求参数
                                        .similarityThreshold(0.5d) // 相似度阈值
                                        .topK(2) // 返回的文档片段数量
                                        .build()
                        )
                )
                .build();
    }

}