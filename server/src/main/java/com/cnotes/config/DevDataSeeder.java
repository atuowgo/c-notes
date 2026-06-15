package com.cnotes.config;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.common.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 仅 dev profile:表为空时灌入 3 条样本(已就绪/处理中/失败),
 * 让前端在无 LLM、无 MySQL 的情况下也能稳定演示三种卡片状态。
 * 生产 profile 不加载此 Bean。
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final ArticleMapper articleMapper;

    @Override
    public void run(String... args) {
        if (articleMapper.selectCount(null) > 0) return;

        articleMapper.insert(done(
            "https://mp.weixin.qq.com/s/demo-attention",
            "Attention Is All You Need:重读经典", "李沐", "wechat",
            "Transformer 用自注意力替代循环结构,让序列建模可以完全并行。本文从动机、架构到训练细节做一次彻底重读。",
            "[\"自注意力让任意两个位置直接交互,路径长度为常数\",\"多头注意力在不同子空间并行捕捉关系\",\"位置编码用正弦函数注入顺序信息\",\"完全并行使大规模预训练成为可能\"]",
            "Transformer 提出用自注意力机制完全替代 RNN/CNN,既缩短了长程依赖的信息传播路径,又解锁了训练并行度,为后续大模型奠定了基础架构。"));

        articleMapper.insert(pending(
            "https://www.example.com/blog/rust-ownership",
            "理解 Rust 所有权与借用", "Steve", "browser",
            "所有权是 Rust 内存安全的核心:每个值有唯一所有者,离开作用域即释放;借用允许在不转移所有权的前提下临时访问。"));

        articleMapper.insert(failed(
            "https://paywall.example.com/deep-dive",
            "分布式共识:从 Paxos 到 Raft", null, "browser",
            "正文抓取失败:目标站点需要登录,Readability 仅取到导航与登录提示。"));
    }

    private Article base(String url, String title, String author, String source, String content) {
        Article a = new Article();
        a.setUrl(url);
        a.setUrlHash(Hashing.md5Hex(url));
        a.setTitle(title);
        a.setAuthor(author);
        a.setSourceType(source);
        a.setContent(content);
        a.setExtractMethod("readability");
        a.setRetryCount(0);
        return a;
    }

    private Article done(String url, String title, String author, String source,
                         String content, String keyPoints, String summary) {
        Article a = base(url, title, author, source, content);
        a.setStatus("done");
        a.setSummary(summary);
        a.setKeyPoints(keyPoints);
        return a;
    }

    private Article pending(String url, String title, String author, String source, String content) {
        Article a = base(url, title, author, source, content);
        a.setStatus("pending");
        return a;
    }

    private Article failed(String url, String title, String author, String source, String content) {
        Article a = base(url, title, author, source, content);
        a.setStatus("failed");
        a.setRetryCount(5);
        a.setLastError("extract_failed: login required");
        return a;
    }
}
