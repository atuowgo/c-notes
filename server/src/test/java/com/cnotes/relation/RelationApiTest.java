package com.cnotes.relation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.relation.entity.ArticleRelation;
import com.cnotes.relation.mapper.ArticleRelationMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P-relation:文章关联 API。{@code GET /api/articles/{id}/related} → 200 + JSON 数组。
 * ChatModel 用 stub bean(@Primary)返回「无可用结果」(空 relations 列表),迫使走兜底:
 * 按共享标签数量取近邻并落库;断言数组非空、relationType=相关,且第二次调用直接读已落库连边
 * (不重算,不再触碰 LLM)。本机真实 LLM 由 DEEPSEEK_API_KEY 门控覆盖(此处不含)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RelationApiTest.StubModelConfig.class)
class RelationApiTest {

    @TestConfiguration
    static class StubModelConfig {
        // 返回结构化但「无可用项」的 JSON:relations 为空 → pickByLlm 空 → 走兜底。
        @Bean @Primary
        ChatModel stubChatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"relations\":[]}"))));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired ArticleRelationMapper relationMapper;

    private String seedDoneArticle(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/r/" + h); a.setUrlHash(h);
        a.setTitle(title); a.setSummary("摘要-" + title); a.setStatus("done");
        a.setKeyPoints("[\"要点1\"]");
        articleMapper.insert(a);
        return a.getId();
    }

    private void link(String articleId, String tagId) {
        ArticleTag t = new ArticleTag();
        t.setArticleId(articleId); t.setTagId(tagId);
        articleTagMapper.insert(t);
    }

    @Test
    void fallbackYieldsRelationsThenSecondCallReadsStored() throws Exception {
        Tag tag = new Tag(); tag.setName("关联测试标签-" + java.util.UUID.randomUUID());
        tagMapper.insert(tag);
        String a1 = seedDoneArticle("文甲");
        String a2 = seedDoneArticle("文乙");
        link(a1, tag.getId());
        link(a2, tag.getId());

        // 计算路径:LLM 返回空 → 兜底按共享标签近邻,落 1 条 a1->a2
        mvc.perform(get("/api/articles/" + a1 + "/related"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].article.id", org.hamcrest.Matchers.is(a2)))
            .andExpect(jsonPath("$[0].relationType", org.hamcrest.Matchers.is("相关")))
            .andExpect(jsonPath("$[0].reason", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
            .andExpect(jsonPath("$[0].article.tags", org.hamcrest.Matchers.hasItem(tag.getName())));

        List<ArticleRelation> stored = relationMapper.selectList(
            Wrappers.<ArticleRelation>lambdaQuery().eq(ArticleRelation::getFromArticleId, a1));
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getToArticleId()).isEqualTo(a2);

        // 第二次调用:直接读已落库连边,不再新增行
        mvc.perform(get("/api/articles/" + a1 + "/related"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].article.id", org.hamcrest.Matchers.is(a2)));

        assertThat(relationMapper.selectList(
            Wrappers.<ArticleRelation>lambdaQuery().eq(ArticleRelation::getFromArticleId, a1)))
            .hasSize(1);
    }

    @Test
    void noCandidatesReturnsEmptyArray() throws Exception {
        Tag tag = new Tag(); tag.setName("孤立标签-" + java.util.UUID.randomUUID());
        tagMapper.insert(tag);
        String solo = seedDoneArticle("孤篇");
        link(solo, tag.getId());

        mvc.perform(get("/api/articles/" + solo + "/related"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void prestoredRelationsReturnedWithoutCompute() throws Exception {
        String a1 = seedDoneArticle("有连边-甲");
        String a2 = seedDoneArticle("有连边-乙");
        ArticleRelation r = new ArticleRelation();
        r.setFromArticleId(a1); r.setToArticleId(a2);
        r.setRelationType("延伸"); r.setReason("乙是甲的深入延伸");
        relationMapper.insert(r);

        mvc.perform(get("/api/articles/" + a1 + "/related"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].article.id", org.hamcrest.Matchers.is(a2)))
            .andExpect(jsonPath("$[0].relationType", org.hamcrest.Matchers.is("延伸")))
            .andExpect(jsonPath("$[0].reason", org.hamcrest.Matchers.is("乙是甲的深入延伸")));
    }
}
