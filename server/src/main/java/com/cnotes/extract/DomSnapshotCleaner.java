package com.cnotes.extract;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 二级抓取(产品设计 §5.4 第 2 级):当插件本地 Readability 提取的正文过薄/不干净时,
 * 把插件提交的<b>渲染后 DOM 快照</b>交给模型清洗,抽出正文主体(去导航/广告/页脚/脚本)。
 *
 * <p>介于第 1 级(插件 Readability,近免费)与第 3 级(服务器无头浏览器,重)之间:
 * 不再发网络(快照已在手),用一次模型调用换更干净的正文。失败/空快照优雅返回 null,上层继续兜底。
 */
@Service
public class DomSnapshotCleaner {

    private final ChatClient chatClient;
    /** 快照可能很大,截断到上限以控住 token 成本(超出部分对正文提取基本无价值)。 */
    private final int maxChars;

    public DomSnapshotCleaner(ChatClient.Builder builder,
                              @Value("${extract.dom-clean.max-chars:60000}") int maxChars) {
        this.chatClient = builder.build();
        this.maxChars = maxChars;
    }

    /** 清洗 DOM 快照,返回纯净正文;空快照或模型失败返回 null。 */
    public String clean(String domSnapshot) {
        if (domSnapshot == null || domSnapshot.isBlank()) return null;
        String html = domSnapshot.length() > maxChars ? domSnapshot.substring(0, maxChars) : domSnapshot;
        try {
            String text = chatClient.prompt()
                .system("你是网页正文提取器。从给定的网页 HTML 中抽取文章正文主体,"
                    + "去掉导航栏、侧边栏、广告、页脚、按钮、脚本与样式,保留标题与段落结构,"
                    + "输出干净的纯文本(可含必要换行),不要添加任何解释或前后缀。")
                .user(u -> u.text("网页 HTML:\n{html}").param("html", html))
                .call()
                .content();
            return text == null || text.isBlank() ? null : text.trim();
        } catch (Exception e) {
            return null;   // 二级清洗不可用:返回 null,上层继续走第 3 级
        }
    }
}
