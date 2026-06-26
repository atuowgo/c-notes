package com.cnotes.link;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
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
 * 关联推荐 HTTP 接口:GET /api/articles/{id}/links 返回 [{targetArticle,linkType,reason,score}]。
 * 打桩 EmbeddingModel/LinkReasoner 隔离外部网络(同 ServiceTest)。路由断言:
 * /api/articles/{id}/links 不会误命中 ArticleController 的 GET /api/articles/{id}。
 *
 * <p>隔离:测试 H2 与其它非 @Transactional 用例共享同一内存库,故用专用 32 字符 owner +
 * 打桩 {@link CurrentUserResolver#currentUserId()} 返回该 owner,使本类只触达自家文章/关联。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkApiTest {

    private static final String OWNER = "2c3d4e5f60718293a4b5c6d7e8f90011";

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired TagMapper tagMapper;

    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean LinkReasoner reasoner;
    @MockitoBean CurrentUserResolver currentUser;

    @BeforeEach
    void stubOwnerAndReasoner() {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(reasoner.reason(any(), any())).thenReturn("同主题");
    }

    private String seedDone(String title, String summary) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/lka/" + h); a.setUrlHash(h);
        a.setOwnerId(OWNER);
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

    @Test
    void getLinksReturnsDtosWithReasonAndScore() throws Exception {
        // A、B 同向量 → cosine 1.0;共享标签 → 命中
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("甲", "x");
        String b = seedDone("乙", "y");
        String t = seedTag("T");
        link(a, t); link(b, t);

        mvc.perform(get("/api/articles/" + a + "/links"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].targetArticle.id", is(b)))
           .andExpect(jsonPath("$[0].targetArticle.title", is("乙")))
           .andExpect(jsonPath("$[0].linkType", is("相关")))
           .andExpect(jsonPath("$[0].reason", is("同主题")))
           .andExpect(jsonPath("$[0].score", is(1.0)));
    }

    @Test
    void getLinksMissingArticleReturnsEmpty() throws Exception {
        mvc.perform(get("/api/articles/" + "0".repeat(32) + "/links"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getLinksNoCandidatesReturnsEmpty() throws Exception {
        // 仅一篇文章、无共享标签候选 → 空推荐(不影响阅读)
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        String a = seedDone("孤篇", "x");
        link(a, seedTag("独有"));

        mvc.perform(get("/api/articles/" + a + "/links"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }
}
