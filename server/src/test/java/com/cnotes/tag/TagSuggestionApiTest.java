package com.cnotes.tag;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.entity.TagSuggestion;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import com.cnotes.tag.mapper.TagSuggestionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TagSuggestionApiTest {

    @Autowired MockMvc mvc;
    @Autowired TagSuggestionMapper suggestionMapper;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired ArticleMapper articleMapper;

    private String seedArticle() {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/" + h); a.setUrlHash(h);
        a.setTitle("测试文章"); a.setStatus("done"); a.setKeyPoints("[]");
        articleMapper.insert(a);
        return a.getId();
    }

    private TagSuggestion seedSuggestion(String articleId, String name) {
        TagSuggestion s = new TagSuggestion();
        s.setArticleId(articleId); s.setName(name); s.setStatus("pending");
        suggestionMapper.insert(s);
        return s;
    }

    @Test
    void listPendingForArticle() throws Exception {
        String articleId = seedArticle();
        seedSuggestion(articleId, "量子计算");
        seedSuggestion(articleId, "神经形态芯片");

        mvc.perform(get("/api/articles/" + articleId + "/tag-suggestions"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(2)))
           .andExpect(jsonPath("$[*].name", hasItems("量子计算", "神经形态芯片")))
           .andExpect(jsonPath("$[0].status", is("pending")));
    }

    @Test
    void listPendingExcludesAcceptedAndRejected() throws Exception {
        String articleId = seedArticle();
        seedSuggestion(articleId, "待确认");
        TagSuggestion accepted = seedSuggestion(articleId, "已接受");
        accepted.setStatus("accepted"); suggestionMapper.updateById(accepted);
        TagSuggestion rejected = seedSuggestion(articleId, "已拒绝");
        rejected.setStatus("rejected"); suggestionMapper.updateById(rejected);

        mvc.perform(get("/api/articles/" + articleId + "/tag-suggestions"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].name", is("待确认")));
    }

    @Test
    void acceptCreatesTagAndLinksArticle() throws Exception {
        String articleId = seedArticle();
        TagSuggestion s = seedSuggestion(articleId, "新主题");

        mvc.perform(post("/api/tags/suggestions/" + s.getId() + "/accept"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status", is("accepted")));

        // 受控标签集里应存在新标签
        assertThat(tagMapper.selectList(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, "新主题"))).hasSize(1);
        // 文章应已链接到该标签
        Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, "新主题"));
        assertThat(articleTagMapper.selectCount(
            Wrappers.lambdaQuery(com.cnotes.tag.entity.ArticleTag.class)
                .eq(com.cnotes.tag.entity.ArticleTag::getArticleId, articleId)
                .eq(com.cnotes.tag.entity.ArticleTag::getTagId, tag.getId()))).isEqualTo(1);
    }

    @Test
    void acceptIsIdempotentWhenTagAlreadyExists() throws Exception {
        String articleId = seedArticle();
        Tag existing = new Tag(); existing.setName("已有标签"); tagMapper.insert(existing);
        TagSuggestion s = seedSuggestion(articleId, "已有标签");

        mvc.perform(post("/api/tags/suggestions/" + s.getId() + "/accept"))
           .andExpect(status().isOk());

        // 标签仍只有一条(未重复创建)
        assertThat(tagMapper.selectList(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, "已有标签"))).hasSize(1);
    }

    @Test
    void rejectUpdatesStatus() throws Exception {
        String articleId = seedArticle();
        TagSuggestion s = seedSuggestion(articleId, "不想要");

        mvc.perform(post("/api/tags/suggestions/" + s.getId() + "/reject"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status", is("rejected")));

        assertThat(tagMapper.selectCount(null)).isEqualTo(0);  // 未创建受控标签
    }

    @Test
    void acceptReturns404ForUnknownId() throws Exception {
        mvc.perform(post("/api/tags/suggestions/nonexistent-id/accept"))
           .andExpect(status().isNotFound());
    }
}
