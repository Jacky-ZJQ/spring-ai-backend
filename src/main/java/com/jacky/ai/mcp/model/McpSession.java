package com.jacky.ai.mcp.model;

import java.time.Instant;

/**
 * MCP 会话状态。
 */
public class McpSession {

    private final String sessionId;
    private final String protocolVersion;
    private volatile boolean initialized;
    private final Instant createdAt;
    private volatile Instant lastSeenAt;

    public McpSession(String sessionId, String protocolVersion, Instant createdAt) {
        this.sessionId = sessionId;
        this.protocolVersion = protocolVersion;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
        this.initialized = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }
}
