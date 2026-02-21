package com.jacky.ai.mcp.service;

import com.jacky.ai.mcp.config.McpProperties;
import com.jacky.ai.mcp.exception.McpProtocolException;
import com.jacky.ai.mcp.model.McpSession;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 会话管理器，负责会话创建、校验、过期控制。
 */
@Component
public class McpSessionManager {

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final long sessionTtlSeconds;

    public McpSessionManager(McpProperties mcpProperties) {
        this.sessionTtlSeconds = mcpProperties.getSessionTtlSeconds() > 0 ? mcpProperties.getSessionTtlSeconds() : 1800;
    }

    public McpSession createSession(String requestedSessionId, String protocolVersion) {
        cleanupExpiredSessions();
        String sessionId = StringUtils.hasText(requestedSessionId) ? requestedSessionId.trim() : UUID.randomUUID().toString();
        McpSession session = new McpSession(sessionId, protocolVersion, Instant.now());
        sessions.put(sessionId, session);
        return session;
    }

    public McpSession requireActiveSession(String sessionIdHeader) {
        cleanupExpiredSessions();
        if (!StringUtils.hasText(sessionIdHeader)) {
            throw new McpProtocolException(-32002, "Missing MCP-Session-Id header");
        }
        McpSession session = sessions.get(sessionIdHeader.trim());
        if (session == null) {
            throw new McpProtocolException(-32002, "Unknown MCP session");
        }
        if (isExpired(session)) {
            sessions.remove(session.getSessionId());
            throw new McpProtocolException(-32002, "MCP session expired");
        }
        session.touch();
        return session;
    }

    public void validateProtocolVersion(McpSession session, String protocolVersionHeader) {
        if (!StringUtils.hasText(protocolVersionHeader)) {
            return;
        }
        if (!session.getProtocolVersion().equals(protocolVersionHeader.trim())) {
            throw new McpProtocolException(-32600, "MCP-Protocol-Version mismatch");
        }
    }

    public void markInitialized(McpSession session) {
        session.markInitialized();
        session.touch();
    }

    private void cleanupExpiredSessions() {
        sessions.values().removeIf(this::isExpired);
    }

    private boolean isExpired(McpSession session) {
        return session.getLastSeenAt().plusSeconds(sessionTtlSeconds).isBefore(Instant.now());
    }
}
