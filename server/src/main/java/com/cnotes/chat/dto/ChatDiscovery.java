package com.cnotes.chat.dto;

/**
 * 深聊联网搜索发现的一条网页结果(产品设计 §2:联网发现的好内容可一键收进知识网)。
 * 由 {@code WebSearchTool} 在 LLM 工具调用期结构化捕获,随 {@link ChatReply} 回给前端,
 * 前端可对每条「一键收藏」走正常 collect 抓取管线。
 */
public record ChatDiscovery(String title, String url, String snippet) {}
