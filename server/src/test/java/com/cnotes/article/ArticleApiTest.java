package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ArticleApiTest {

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;

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
}
