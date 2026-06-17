package com.cnotes.chat.dto;

/** 深聊请求:用户消息 + 可选续聊会话 id(为空则新建会话)。 */
public record ChatRequest(String message, String sessionId) {}
