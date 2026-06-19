package com.cnotes.tag;

import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.entity.TagMerge;
import com.cnotes.tag.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TagClassifierTest {

    @Autowired TagClassifier classifier;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired TagSuggestionMapper suggestionMapper;
    @Autowired TagMergeMapper tagMergeMapper;

    @Test
    void hitGoesToArticleTagMissGoesToSuggestion() {
        Tag t = new Tag(); t.setName("Rust"); tagMapper.insert(t);
        String articleId = "a1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, List.of("Rust", "某新概念"));
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }

    @Test
    void applyIsIdempotent() {
        Tag t = new Tag(); t.setName("LLM"); tagMapper.insert(t);
        String articleId = "a2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, List.of("LLM", "新X"));
        classifier.apply(articleId, List.of("LLM", "新X"));
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }

    @Test
    void mergedTagRedirectsToTarget() {
        Tag from = new Tag(); from.setName("旧名" + java.util.UUID.randomUUID());
        from.setArchived(true); tagMapper.insert(from);
        Tag to = new Tag(); to.setName("新名" + java.util.UUID.randomUUID()); tagMapper.insert(to);
        TagMerge m = new TagMerge(); m.setFromTagId(from.getId()); m.setToTagId(to.getId());
        tagMergeMapper.insert(m);

        String articleId = "a3aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        // 模型给出已合并的旧标签名 -> 应改投到 to,且来源为 ai
        classifier.apply(articleId, List.of(from.getName()));

        var links = articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId));
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getTagId()).isEqualTo(to.getId());
        assertThat(links.get(0).getSource()).isEqualTo("ai");
    }

    @Test
    void archivedTagWithoutRedirectIsSkipped() {
        Tag archived = new Tag(); archived.setName("已归档" + java.util.UUID.randomUUID());
        archived.setArchived(true); tagMapper.insert(archived);

        String articleId = "a4aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, List.of(archived.getName()));

        // 归档且无重定向 -> 既不建链接,也不进建议
        assertThat(articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId))).isEmpty();
    }
}
