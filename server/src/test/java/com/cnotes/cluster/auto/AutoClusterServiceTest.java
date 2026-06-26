package com.cnotes.cluster.auto;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.auto.entity.AutoCluster;
import com.cnotes.cluster.auto.mapper.AutoClusterMapper;
import com.cnotes.user.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 语义簇服务:打桩 {@link EmbeddingModel}(@Primary 的 ArkEmbeddingModel)隔离 Ark 网络,
 * 打桩 {@link AutoClusterSummarizer} 隔离 chat 网络——与 ClusterServiceTest 隔离
 * ClusterSummarizer/ClusterIndexer 的思路一致。用受控向量驱动 cosine 聚类断言。
 *
 * <p>隔离:测试 H2 与 ArticleApiTest 等(非 @Transactional)共享同一内存库,
 * 故用专用 32 字符 owner + 打桩 {@link CurrentUserResolver#currentUserId()} 返回该 owner,
 * 使本类的 list/detail/recompute 只触达自家文章/簇,不受共享库污染。
 */
@SpringBootTest
@Transactional
class AutoClusterServiceTest {

    /** 专用 owner(32 hex,CHAR(32) 无空格填充问题;不与真实/Seeder 用户碰撞)。 */
    private static final String OWNER = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";

    @Autowired AutoClusterService autoClusterService;
    @Autowired ArticleMapper articleMapper;
    @Autowired AutoClusterMapper autoClusterMapper;

    @MockitoBean EmbeddingModel embeddingModel;       // 隔离 Ark embedding 网络
    @MockitoBean AutoClusterSummarizer summarizer;    // 隔离 chat 综述网络
    @MockitoBean CurrentUserResolver currentUser;     // 隔离共享库:固定当前用户为 OWNER

    @BeforeEach
    void stubOwner() {
        when(currentUser.currentUserId()).thenReturn(OWNER);
    }

    private String seedDone(String title, String summary) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/ac/" + h); a.setUrlHash(h);
        a.setOwnerId(OWNER);
        a.setTitle(title); a.setSummary(summary); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    private long countOwnerClusters() {
        return autoClusterMapper.selectCount(
            Wrappers.<AutoCluster>lambdaQuery().eq(AutoCluster::getOwnerId, OWNER));
    }

    @Test
    void recomputeMergesSimilarAndDropsSingleton() {
        // A、B 语义相同(同 markerA → 同向量 → cosine 1.0 ≥ 阈值 → 合并为一簇);
        // C 语义不同(markerB → 正交向量 → 与 A/B cosine 0 < 阈值 → 单篇不落库)
        float[] same = {1f, 0f};
        float[] orth = {0f, 1f};
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.contains("markerA") ? same : orth;
        });
        when(summarizer.summarize(any(), any())).thenReturn("语义综述正文");

        seedDone("聚类甲 markerA", "x");
        seedDone("聚类乙 markerA", "y");
        seedDone("聚类丙 markerB", "z");   // C:单篇,不进簇

        autoClusterService.recomputeForOwner(OWNER);

        var list = autoClusterService.listAutoClusters();
        assertThat(list).hasSize(1);
        var card = list.get(0);
        assertThat(card.getMemberCount()).isEqualTo(2);
        assertThat(card.isHasSummary()).isTrue();
        assertThat(card.getSummary()).isEqualTo("语义综述正文");

        var detail = autoClusterService.detail(card.getId());
        assertThat(detail.getArticles()).hasSize(2);
        assertThat(detail.getArticles().stream().map(ArticleCardDto::getTitle).toList())
            .containsExactlyInAnyOrder("聚类甲 markerA", "聚类乙 markerA");

        verify(summarizer).summarize(any(), any());
    }

    @Test
    void recomputeIsIdempotent_clearsStaleOnRerun() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(summarizer.summarize(any(), any())).thenReturn("综述");
        seedDone("甲", "x");
        seedDone("乙", "y");
        autoClusterService.recomputeForOwner(OWNER);
        assertThat(countOwnerClusters()).isEqualTo(1);

        // 再次重算:先删后插,簇不翻倍(幂等)
        autoClusterService.recomputeForOwner(OWNER);
        assertThat(countOwnerClusters()).isEqualTo(1);
    }

    @Test
    void tooFewArticlesProducesNoCluster() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        seedDone("独苗 markerA", "x");   // 仅 1 篇 < minMembers(2)
        autoClusterService.recomputeForOwner(OWNER);
        assertThat(autoClusterService.listAutoClusters()).isEmpty();
    }
}
