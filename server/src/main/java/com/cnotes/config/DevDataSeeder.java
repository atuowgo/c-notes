package com.cnotes.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.common.Hashing;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import com.cnotes.user.entity.User;
import com.cnotes.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 仅 dev profile:表为空时灌入 3 条样本(已就绪/处理中/失败),
 * 让前端在无 LLM、无 MySQL 的情况下也能稳定演示三种卡片状态。
 * 生产 profile 不加载此 Bean。
 *
 * <p>A1 多用户:额外创建 demo 用户(demo/demo123),并把现有 seed 数据 owner_id 回填给它,
 * 让登录后的演示账号能直接看到样本文章。
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final ArticleMapper articleMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 当前 seed 批次归属的 demo 用户 id(run() 内赋值,供 base() 设置 owner_id)。 */
    private String demoOwnerId;

    @Override
    public void run(String... args) {
        this.demoOwnerId = ensureDemoUser();
        backfillOwner(demoOwnerId);
        if (articleMapper.selectCount(null) > 0) return;

        Article attention = done(
            "https://mp.weixin.qq.com/s/demo-attention",
            "Attention Is All You Need:重读经典", "李沐", "wechat",
            "Transformer 用自注意力替代循环结构,让序列建模可以完全并行。本文从动机、架构到训练细节做一次彻底重读。",
            "[\"自注意力让任意两个位置直接交互,路径长度为常数\",\"多头注意力在不同子空间并行捕捉关系\",\"位置编码用正弦函数注入顺序信息\",\"完全并行使大规模预训练成为可能\"]",
            "Transformer 提出用自注意力机制完全替代 RNN/CNN,既缩短了长程依赖的信息传播路径,又解锁了训练并行度,为后续大模型奠定了基础架构。");
        articleMapper.insert(attention);
        tagArticle(attention.getId(), "LLM 推理优化", "深度学习");

        // 第二篇「深度学习」已就绪文章:让该标签满足 cluster.min-members(=2),
        // 触发 ClusterSummaryWorker 真实跑「DeepSeek 综述 → Ark 向量化 → SimpleVectorStore 落盘」,
        // 从而深聊的 🕸 知识网来源可在端到端链路中被真实命中。
        Article resnet = done(
            "https://mp.weixin.qq.com/s/demo-resnet",
            "深度残差网络 ResNet:让千层网络可训练", "何恺明", "wechat",
            "ResNet 通过残差连接缓解深层网络的退化问题,使上百层乃至上千层的网络也能稳定收敛。本文重读其动机与设计。",
            "[\"残差连接让梯度可以直接回传,缓解梯度消失\",\"恒等映射使更深的网络至少不劣于浅层\",\"瓶颈结构在保持表达力的同时降低计算量\",\"批归一化与残差协同稳定训练\"]",
            "ResNet 以残差学习重构深层网络的优化目标,用跳跃连接让极深网络可训练,成为现代视觉与多领域骨干网络的基础。");
        articleMapper.insert(resnet);
        tagArticle(resnet.getId(), "深度学习");

        Article rust = pending(
            "https://www.example.com/blog/rust-ownership",
            "理解 Rust 所有权与借用", "Steve", "browser",
            "所有权是 Rust 内存安全的核心:每个值有唯一所有者,离开作用域即释放;借用允许在不转移所有权的前提下临时访问。");
        articleMapper.insert(rust);
        tagArticle(rust.getId(), "Rust", "系统编程");

        articleMapper.insert(failed(
            "https://paywall.example.com/deep-dive",
            "分布式共识:从 Paxos 到 Raft", null, "browser",
            "正文抓取失败:目标站点需要登录,Readability 仅取到导航与登录提示。"));
    }

    /** 受控标签集中没有就建,再把文章挂上去(dev 演示用)。 */
    private void tagArticle(String articleId, String... tagNames) {
        for (String name : tagNames) {
            Tag tag = tagMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name))
                .stream().findFirst().orElse(null);
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tagMapper.insert(tag);
            }
            ArticleTag link = new ArticleTag();
            link.setArticleId(articleId);
            link.setTagId(tag.getId());
            articleTagMapper.insert(link);
        }
    }

    private Article base(String url, String title, String author, String source, String content) {
        Article a = new Article();
        a.setOwnerId(demoOwnerId);
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

    /** 幂等创建 demo 用户(demo/demo123),返回其 id。 */
    private String ensureDemoUser() {
        User existing = userMapper.selectOne(
            Wrappers.<User>lambdaQuery().eq(User::getUsername, "demo"));
        if (existing != null) return existing.getId();
        User u = new User();
        u.setUsername("demo");
        u.setPasswordHash(passwordEncoder.encode("demo123"));
        userMapper.insert(u);
        return u.getId();
    }

    /** 历史/无主 article 回填给 demo 用户(owner_id IS NULL → demoId)。 */
    private void backfillOwner(String ownerId) {
        Article patch = new Article();
        patch.setOwnerId(ownerId);
        articleMapper.update(patch, Wrappers.<Article>lambdaUpdate().isNull(Article::getOwnerId));
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
