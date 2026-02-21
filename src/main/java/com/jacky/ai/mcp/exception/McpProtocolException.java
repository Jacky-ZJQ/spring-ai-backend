package com.jacky.ai.mcp.exception;

/**
 * MCP 协议异常，携带 JSON-RPC 错误码。
 */
public class McpProtocolException extends RuntimeException {

    private final int code;

    public McpProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
