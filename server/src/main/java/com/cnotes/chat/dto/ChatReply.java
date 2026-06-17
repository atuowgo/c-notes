package com.cnotes.chat.dto;

import java.util.List;

/** 深聊回复:会话 id + 助手回复正文 + 本轮实际启用的源标签(📄 本文 / 🕸 知识网 / 🌐 联网)。 */
public record ChatReply(String sessionId, String reply, List<String> sources) {}
