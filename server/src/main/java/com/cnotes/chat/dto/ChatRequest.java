package com.cnotes.chat.dto;

/**
 * 深聊请求:用户消息 + 可选续聊会话 id(为空则新建会话)+ 可选锚定想法 id。
 * noteId 由阅读端「提问」带入——把该条想法作为第四层来源 💭 我的想法。
 */
public record ChatRequest(String message, String sessionId, String noteId) {
    /** 兼容两参构造(无锚定想法)。 */
    public ChatRequest(String message, String sessionId) {
        this(message, sessionId, null);
    }
}
