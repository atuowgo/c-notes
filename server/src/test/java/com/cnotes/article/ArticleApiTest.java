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

    /** 测试默认以系统用户(匿名)身份读收件箱,故落库文章归属系统用户方可见。 */
    private static final String OWNER = com.cnotes.auth.entity.User.SYSTEM_ID;

    private String seed() {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setOwnerId(OWNER);
        a.setUrl("https://e.com/list/" + h); a.setUrlHash(h);
        a.setTitle("收件箱标题"); a.setSummary("摘要"); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    /** 用指定标题落一篇文章,返回 id;标题用 ASCII 以便从响应体直接断言。 */
    private String seedTitled(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setOwnerId(OWNER);
        a.setUrl("https://e.com/pg/" + h); a.setUrlHash(h);
        a.setTitle(title); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    /**
     * 落一篇带显式 create_time 的文章。H2(MODE=MySQL)下 DATETIME 仅秒级精度,
     * 同一秒内插入的多行 create_time 全部相等,orderByDesc 退化为任意序;
     * 显式给定互异且最新的时间戳可让"最新在前"的分页断言确定可复现
     * (AutoFillHandler 用 strictInsertFill,字段非空时不覆盖)。
     */
    private void seedTitledAt(String title, java.time.LocalDateTime createTime) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setOwnerId(OWNER);
        a.setUrl("https://e.com/pg/" + h); a.setUrlHash(h);
        a.setTitle(title); a.setStatus("done");
        a.setCreateTime(createTime);
        articleMapper.insert(a);
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
        a.setOwnerId(OWNER);
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

    /** 收件箱必须有界:size 决定单页条数,绝不一次返回全表。 */
    @Test
    void inboxCapsToRequestedSize() throws Exception {
        seedTitled("capA-" + System.nanoTime());
        seedTitled("capB-" + System.nanoTime());
        seedTitled("capC-" + System.nanoTime());
        mvc.perform(get("/api/articles?size=2"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(2)));
    }

    /** page 偏移生效:第 3 篇(最旧的新插入)只能在第二页取到,不在第一页。 */
    @Test
    void inboxPaginatesAcrossPages() throws Exception {
        String t1 = "pgX-" + System.nanoTime();
        String t2 = "pgY-" + System.nanoTime();
        String t3 = "pgZ-" + System.nanoTime();
        // 显式给 3 个互异且远未来的时间戳,保证它们是全表最新的三行且顺序确定:t1>t2>t3。
        java.time.LocalDateTime base = java.time.LocalDateTime.of(2099, 1, 1, 0, 0, 0);
        seedTitledAt(t1, base.plusSeconds(3));
        seedTitledAt(t2, base.plusSeconds(2));
        seedTitledAt(t3, base.plusSeconds(1));

        String page1 = mvc.perform(get("/api/articles?page=1&size=2"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(2)))
           .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String page2 = mvc.perform(get("/api/articles?page=2&size=2"))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 三篇最新插入占据前三槽位;两页并集必须覆盖全部三篇,证明偏移取到了第一页之外的行。
        String union = page1 + page2;
        org.assertj.core.api.Assertions.assertThat(union).contains(t1).contains(t2).contains(t3);
        // 且第一页装不下三篇,第 3 篇必然来自第二页 —— 偏移真实生效。
        org.assertj.core.api.Assertions.assertThat(page1.contains(t1) && page1.contains(t2) && page1.contains(t3))
            .as("第一页不应同时装下三篇").isFalse();
    }

    /** 分页需回报总数,前端"加载更多"据此判断到底。 */
    @Test
    void inboxReportsTotalCount() throws Exception {
        seedTitled("total-" + System.nanoTime());
        mvc.perform(get("/api/articles?page=1&size=1"))
           .andExpect(status().isOk())
           .andExpect(header().string("X-Total-Count", matchesPattern("\\d+")));
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
        a.setOwnerId(OWNER);
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
