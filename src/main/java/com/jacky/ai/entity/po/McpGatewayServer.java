package com.jacky.ai.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("mcp_gateway_server")
public class McpGatewayServer implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("server_name")
    private String serverName;

    @TableField("connection_type")
    private String connectionType;

    @TableField("connection_url")
    private String connectionUrl;

    @TableField("sse_endpoint")
    private String sseEndpoint;

    @TableField("stdio_command")
    private String stdioCommand;

    @TableField("stdio_args_json")
    private String stdioArgsJson;

    @TableField("stdio_env_json")
    private String stdioEnvJson;

    @TableField("is_code_client")
    private Boolean codeClient;

    @TableField("is_ping_available")
    private Boolean pingAvailable;

    @TableField("tool_sync_interval_minutes")
    private Integer toolSyncIntervalMinutes;

    @TableField("headers_json")
    private String headersJson;

    @TableField("auth_type")
    private String authType;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
