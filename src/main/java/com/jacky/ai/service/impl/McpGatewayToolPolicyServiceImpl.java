package com.jacky.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jacky.ai.entity.po.McpGatewayToolPolicy;
import com.jacky.ai.mapper.McpGatewayToolPolicyMapper;
import com.jacky.ai.service.IMcpGatewayToolPolicyService;
import org.springframework.stereotype.Service;

@Service
public class McpGatewayToolPolicyServiceImpl extends ServiceImpl<McpGatewayToolPolicyMapper, McpGatewayToolPolicy>
        implements IMcpGatewayToolPolicyService {
}
