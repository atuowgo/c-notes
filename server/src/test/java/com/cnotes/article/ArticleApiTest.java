package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ArticleApiTest {

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleTagMapper articleTagMapper;

    private String seed() {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/list/" + h); a.setUrlHash(h);
        a.setTitle("收件箱标题"); a.setSummary("摘要"); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    void inboxListsArticles() throws Exception {
        seed();
        mvc.perform(get("/api/articles"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].title", hasItem("收件箱标题")));
    }

    @Test
    void detailReturnsArticle() throws Exception {
        String id = seed();
        mvc.perform(get("/api/articles/" + id))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title", is("收件箱标题")));
    }

    /**
     * 回归:key_points 以 JSON 数组字符串入库,detail 必须能反序列化成数组。
     * 历史上 key_points 列为 JSON 类型时,H2 会把整段数组当成 JSON 字符串标量
     * 二次编码,读回后解析失败静默退化为空数组 —— 此用例守住该回归。
     */
    @Test
    void detailDeserializesKeyPoints() throws Exception {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/kp/" + h); a.setUrlHash(h);
        a.setTitle("要点文章"); a.setStatus("done"); a.setSourceType("wechat");
        a.setKeyPoints("[\"要点A\",\"要点B\",\"要点C\"]");
        articleMapper.insert(a);

        mvc.perform(get("/api/articles/" + a.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sourceType", is("wechat")))
           .andExpect(jsonPath("$.keyPoints", hasSize(3)))
           .andExpect(jsonPath("$.keyPoints[0]", is("要点A")))
           .andExpect(jsonPath("$.keyPoints[2]", is("要点C")));
    }

    /**
     * 卡片与详情应带出已归类的标签名;详情还应带原文 url(用于"看原文")。
     * 标 @Transactional:本用例向全局 article_tag 插入数据,事务回滚以免污染
     * TagClassifierTest 的全表条数断言(共享 H2 内存库)。MockMvc 与测试同线程同事务,
     * 故 GET 仍能读到未提交的插入。
     */
    @Test
    @Transactional
    void cardAndDetailExposeTagsAndUrl() throws Exception {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/tagged/" + h); a.setUrlHash(h);
        a.setTitle("带标签的文章"); a.setStatus("done");
        articleMapper.insert(a);

        Tag t = new Tag(); t.setName("LLM 推理优化-" + h.substring(0, 6));
        tagMapper.insert(t);
        ArticleTag link = new ArticleTag();
        link.setArticleId(a.getId()); link.setTagId(t.getId());
        articleTagMapper.insert(link);

        mvc.perform(get("/api/articles/" + a.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url", is("https://e.com/tagged/" + h)))
           .andExpect(jsonPath("$.tags", hasItem(t.getName())));

        mvc.perform(get("/api/articles"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.id == '" + a.getId() + "')].tags[0]", hasItem(t.getName())));
    }
}
