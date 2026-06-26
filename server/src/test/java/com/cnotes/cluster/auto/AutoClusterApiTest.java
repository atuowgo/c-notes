package com.cnotes.cluster.auto;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 语义簇 HTTP 接口:GET /api/clusters/auto 列表、GET /api/clusters/auto/{id} 详情。
 * 打桩 EmbeddingModel/AutoClusterSummarizer 隔离外部网络(同 ServiceTest)。
 * 路由断言:/api/clusters/auto 不会误命中标签簇 GET /api/clusters/{id}。
 *
 * <p>隔离:测试 H2 与 ArticleApiTest 等(非 @Transactional)共享同一内存库,
 * 故用专用 32 字符 owner + 打桩 {@link CurrentUserResolver#currentUserId()} 返回该 owner,
 * 使本类的 list/detail/recompute 只触达自家文章/簇,不受共享库污染。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutoClusterApiTest {

    private static final String OWNER = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired AutoClusterMapper autoClusterMapper;
    @Autowired AutoClusterService autoClusterService;

    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean AutoClusterSummarizer summarizer;
    @MockitoBean CurrentUserResolver currentUser;

    @BeforeEach
    void stubOwner() {
        when(currentUser.currentUserId()).thenReturn(OWNER);
    }

    private String seedDone(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/aca/" + h); a.setUrlHash(h);
        a.setOwnerId(OWNER);
        a.setTitle(title); a.setSummary("s"); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    void listAndDetailExposeAutoCluster() throws Exception {
        // 两篇同向量 → cosine 1.0 ≥ 阈值 → 合并为一簇(2 篇)
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(summarizer.summarize(any(), any())).thenReturn("语义综述");
        seedDone("甲");
        seedDone("乙");
        autoClusterService.recomputeForOwner(OWNER);

        mvc.perform(get("/api/clusters/auto"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].memberCount", is(2)))
           .andExpect(jsonPath("$[0].hasSummary", is(true)))
           .andExpect(jsonPath("$[0].summary", is("语义综述")));

        AutoCluster c = autoClusterMapper.selectList(
            Wrappers.<AutoCluster>lambdaQuery().eq(AutoCluster::getOwnerId, OWNER)).get(0);
        mvc.perform(get("/api/clusters/auto/" + c.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id", is(c.getId())))
           .andExpect(jsonPath("$.memberCount", is(2)))
           .andExpect(jsonPath("$.articles", hasSize(2)));
    }

    @Test
    void listEmptyWhenNoClusters() throws Exception {
        mvc.perform(get("/api/clusters/auto"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void detailMissingReturns404() throws Exception {
        mvc.perform(get("/api/clusters/auto/" + "0".repeat(32)))
           .andExpect(status().isNotFound());
    }
}
