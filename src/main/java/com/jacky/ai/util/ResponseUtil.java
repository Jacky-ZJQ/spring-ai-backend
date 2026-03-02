package com.jacky.ai.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局响应工具类
 * 用于构建统一的API响应格式
 */
public class ResponseUtil {

    /**
     * 构建成功响应
     * @param data 响应数据
     * @param msg 成功消息
     * @return 包含ok、msg、data的响应Map
     */
    public static Map<String, Object> success(Object data, String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", 1);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }

    /**
     * 构建失败响应
     * @param msg 失败消息
     * @return 包含ok、msg、data的响应Map
     */
    public static Map<String, Object> fail(String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", 0);
        result.put("msg", msg == null ? "操作失败" : msg);
        result.put("data", null);
        return result;
    }
}