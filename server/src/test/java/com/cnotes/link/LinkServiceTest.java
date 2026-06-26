package com.cnotes.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.auto.AutoClusterService;
import com.cnotes.cluster.auto.dto.AutoClusterCardDto;
import com.cnotes.cluster.auto.dto.AutoClusterDetailDto;
import com.cnotes.link.entity.ArticleLink;
import com.cnotes.link.mapper.LinkMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import com.cnotes.user.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关联推荐服务:打桩 {@link EmbeddingModel}(@Primary 的 ArkEmbeddingModel)隔离 Ark 网络,
 * 打桩 {@link LinkReasoner} 隔离 chat 网络(同 AutoClusterServiceTest 打桩 Summarizer 思路)。
 * 用受控向量驱动 cosine 排序断言。
 *
 * <p>隔离:测试 H2 与其它非 @Transactional 用例共享同一内存库,故用专用 32 字符 owner +
 * 打桩 {@link CurrentUserResolver#currentUserId()} 返回该 owner,使本类只触达自家文章/关联。
 */
@SpringBootTest
@Transactional
class LinkServiceTest {

    private static final String OWNER = "1b2c3d4e5f60718293a4b5c6d7e8f900";
    private static final String OTHER = "3d4e5f60718293a4b5c6d7e8f9001122";

    @Autowired LinkService linkService;
    @Autowired ArticleMapper articleMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired TagMapper tagMapper;
    @Autowired LinkMapper linkMapper;

    @MockitoBean EmbeddingModel embeddingModel;     // 隔离 Ark embedding 网络
    @MockitoBean LinkReasoner reasoner;             // 隔离 chat 理由网络
    @MockitoBean CurrentUserResolver currentUser;   // 隔离共享库:固定当前用户为 OWNER
    @MockitoBean AutoClusterService autoClusterService;  // 隔离 B1 语义簇:打桩同簇成员

    @BeforeEach
    void stubOwnerAndReasoner() {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(reasoner.reason(any(), any())).thenReturn("同主题");
    }

    private String seedDone(String title, String summary, String owner) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/lk/" + h); a.setUrlHash(h);
        a.setOwnerId(owner);
        a.setTitle(title); a.setSummary(summary); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    private String seedTag(String name) {
        Tag t = new Tag();
        t.setName(name + "-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        tagMapper.insert(t);
        return t.getId();
    }

    private void link(String articleId, String tagId) {
        ArticleTag at = new ArticleTag();
        at.setArticleId(articleId); at.setTagId(tagId);
        articleTagMapper.insert(at);
    }

    private List<ArticleLink> linksOf(String articleId) {
        return linkMapper.selectList(Wrappers.<ArticleLink>lambdaQuery()
            .eq(ArticleLink::getArticleId, articleId)
            .eq(ArticleLink::getOwnerId, OWNER)
            .orderByDesc(ArticleLink::getScore));
    }

    @Test
    void computesTopNByCosineAndStoresReason() {
        // A、B 同向量(cosine 1.0);C 不同向量(cosine≈0.707);均与 A 共享标签 → 候选
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.contains("markerA") ? new float[]{1f, 0f} : new float[]{1f, 1f};
        });
        String a = seedDone("甲 markerA", "x", OWNER);
        String b = seedDone("乙 markerA", "y", OWNER);
        String c = seedDone("丙 markerC", "z", OWNER);
        String t1 = seedTag("T1"); String t2 = seedTag("T2");
        link(a, t1); link(b, t1);     // A、B 共享 T1
        link(a, t2); link(c, t2);     // A、C 共享 T2

        linkService.computeAndPersist(articleMapper.selectById(a));

        List<ArticleLink> links = linksOf(a);
        assertThat(links).hasSize(2);
        assertThat(links.get(0).getTargetArticleId()).isEqualTo(b);   // cosine 1.0 居首
        assertThat(links.get(0).getScore()).isCloseTo(1.0, within(0.001));
        assertThat(links.get(1).getTargetArticleId()).isEqualTo(c);
        assertThat(links.get(1).getScore()).isCloseTo(0.7071, within(0.01));
        assertThat(links).allMatch(l -> "相关".equals(l.getLinkType()));
        assertThat(links).allMatch(l -> "同主题".equals(l.getReason()));
        verify(reasoner, times(2)).reason(any(), any());
    }

    @Test
    void noSharedTagsProducesNoLinks() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OWNER);
        link(a, seedTag("独有A"));     // A、B 各占独有标签,不共享
        link(b, seedTag("独有B"));

        linkService.computeAndPersist(articleMapper.selectById(a));

        assertThat(linksOf(a)).isEmpty();
    }

    @Test
    void candidatesScopedToSameOwner() {
        // B 属他主:虽与 A 共享标签,候选过滤按 owner 排除 → 无关联
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OTHER);
        String t = seedTag("T"); link(a, t); link(b, t);

        linkService.computeAndPersist(articleMapper.selectById(a));

        assertThat(linksOf(a)).isEmpty();
    }

    @Test
    void recomputeIsIdempotentClearsStaleOnRerun() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OWNER);
        String t = seedTag("T"); link(a, t); link(b, t);
        Article src = articleMapper.selectById(a);

        linkService.computeAndPersist(src);
        assertThat(linksOf(a)).hasSize(1);

        linkService.computeAndPersist(src);   // 再算:先删后插,不翻倍
        assertThat(linksOf(a)).hasSize(1);
    }

    @Test
    void listLinksLazilyComputesWhenEmptyThenHitsCache() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OWNER);
        String t = seedTag("T"); link(a, t); link(b, t);

        var dtos = linkService.listLinks(a);          // 库空 → 懒算
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getTargetArticle().getId()).isEqualTo(b);
        assertThat(dtos.get(0).getLinkType()).isEqualTo("相关");
        assertThat(dtos.get(0).getScore()).isCloseTo(1.0, within(0.001));

        var dtos2 = linkService.listLinks(a);         // 二次读命中库存
        assertThat(dtos2).hasSize(1);
    }

    // ---- 更深入(B4:同 auto_cluster 成员,LLM 判定+理由)---------------------------------

    /** 打桩一个含 source 的语义簇:detail.articles = members。clusterId 固定 "cl-test"。 */
    private void stubClusterWith(ArticleCardDto... members) {
        AutoClusterCardDto card = new AutoClusterCardDto();
        card.setId("cl-test"); card.setMemberCount(members.length);
        when(autoClusterService.listAutoClusters()).thenReturn(List.of(card));
        AutoClusterDetailDto det = new AutoClusterDetailDto();
        det.setId("cl-test"); det.setMemberCount(members.length);
        det.setArticles(List.of(members));
        when(autoClusterService.detail("cl-test")).thenReturn(det);
    }

    private ArticleCardDto card(String id, String title, String summary) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(id); c.setTitle(title); c.setSummary(summary);
        return c;
    }

    @Test
    void deeperLinksFromSameAutoClusterMembers() {
        // A、B 同向量(cosine 1.0);无共享标签 → 不产"相关";同属一 auto_cluster → 产"更深入"
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OWNER);
        stubClusterWith(card(a, "甲", "x"), card(b, "乙", "y"));
        when(reasoner.deeperReason(any(), any())).thenReturn("深挖某点");

        linkService.computeAndPersist(articleMapper.selectById(a));

        List<ArticleLink> links = linksOf(a);
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getTargetArticleId()).isEqualTo(b);
        assertThat(links.get(0).getLinkType()).isEqualTo("更深入");
        assertThat(links.get(0).getReason()).isEqualTo("深挖某点");
        assertThat(links.get(0).getScore()).isCloseTo(1.0, within(0.001));
        verify(reasoner).deeperReason(any(), any());
    }

    @Test
    void deeperDedupsTargetsAlreadyRelated() {
        // A、B 共享标签(cosine 1.0)→ B 作"相关";A、B、C 同簇 → 更深入 候选含 B、C;
        // B 已作"相关"→ 不重复;C 走 LLM 判定 → 入库"更深入"。最终:1 相关 + 1 更深入。
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OWNER);
        String c = seedDone("丙", "z", OWNER);
        String t = seedTag("T"); link(a, t); link(b, t);     // A、B 共享 T → 相关
        stubClusterWith(card(a, "甲", "x"), card(b, "乙", "y"), card(c, "丙", "z"));
        when(reasoner.deeperReason(any(), any())).thenReturn("同簇深挖");

        linkService.computeAndPersist(articleMapper.selectById(a));

        List<ArticleLink> links = linksOf(a);
        assertThat(links).hasSize(2);
        var byTarget = links.stream().collect(java.util.stream.Collectors.toMap(
            ArticleLink::getTargetArticleId, l -> l));
        assertThat(byTarget.get(b).getLinkType()).isEqualTo("相关");   // B 不重复作更深入
        assertThat(byTarget.get(c).getLinkType()).isEqualTo("更深入");
        // B 已作相关 → 不再调用 deeperReason 判定它;仅 C 被判定
        verify(reasoner).deeperReason(any(), any());
    }

    @Test
    void deeperSkipsWhenLlmRejects() {
        // A、B 同簇、无共享标签(无相关);LLM 判 B 非更深入(null)→ 跳过 → 更深入 为空
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x", OWNER);
        String b = seedDone("乙", "y", OWNER);
        stubClusterWith(card(a, "甲", "x"), card(b, "乙", "y"));
        when(reasoner.deeperReason(any(), any())).thenReturn(null);   // LLM 判否

        linkService.computeAndPersist(articleMapper.selectById(a));

        assertThat(linksOf(a)).isEmpty();
        verify(reasoner).deeperReason(any(), any());
    }
}
