package com.jacky.ai.entity.dto.mcpgateway.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP网关服务器创建请求DTO
 * 用于封装创建MCP (Model Context Protocol) 网关服务器所需的配置信息
 */
@Data
public class McpGatewayServerCreateRequest {

    /**
     * 服务器名称，用于唯一标识该MCP网关服务器
     */
    private String serverName;

    /**
     * 连接类型，指定服务器使用的连接方式
     * 支持的类型包括：SSE（Server-Sent Events）、STDIO等
     */
    private String connectionType;

    /**
     * 连接URL，服务器的访问地址
     */
    private String connectionUrl;

    /**
     * SSE端点地址，用于SSE连接类型的端点路径
     */
    private String sseEndpoint;

    /**
     * STDIO启动命令，用于STDIO连接类型的可执行命令
     */
    private String stdioCommand;

    /**
     * STDIO命令参数列表，用于启动STDIO进程的命令行参数
     */
    private List<String> stdioArgs;

    /**
     * STDIO环境变量配置，用于启动STDIO进程的环境变量键值对
     */
    private Map<String, String> stdioEnv;

    /**
     * 是否为代码客户端，标识该服务器的客户端类型
     */
    private Boolean isCodeClient;

    /**
     * 是否支持Ping功能，用于服务器健康检查和可用性检测
     */
    private Boolean isPingAvailable;

    /**
     * 工具同步间隔时间（分钟），指定工具列表的同步频率
     */
    private Integer toolSyncIntervalMinutes;

    /**
     * HTTP请求头配置，用于连接时的认证或其他自定义头信息
     */
    private Map<String, String> headers;

    /**
     * 认证类型，指定连接使用的认证方式
     */
    private String authType;

    /**
     * 是否启用，控制服务器的激活状态
     */
    private Boolean enabled;
}