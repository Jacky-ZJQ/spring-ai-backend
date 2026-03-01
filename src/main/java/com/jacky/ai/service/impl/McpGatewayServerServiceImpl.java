package com.jacky.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jacky.ai.entity.po.McpGatewayServer;
import com.jacky.ai.mapper.McpGatewayServerMapper;
import com.jacky.ai.service.IMcpGatewayServerService;
import org.springframework.stereotype.Service;

@Service
public class McpGatewayServerServiceImpl extends ServiceImpl<McpGatewayServerMapper, McpGatewayServer>
        implements IMcpGatewayServerService {
}
