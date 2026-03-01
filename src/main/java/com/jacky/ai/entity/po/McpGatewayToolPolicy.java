package com.jacky.ai.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("mcp_gateway_tool_policy")
public class McpGatewayToolPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("server_id")
    private Long serverId;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_description")
    private String toolDescription;

    @TableField("input_schema_json")
    private String inputSchemaJson;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("auto_execute")
    private Boolean autoExecute;

    @TableField("cost_usd")
    private BigDecimal costUsd;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
