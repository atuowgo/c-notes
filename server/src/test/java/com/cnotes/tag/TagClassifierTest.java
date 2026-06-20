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

    private static final String OWNER = "o".repeat(32);

    @Test
    void hitGoesToArticleTagMissGoesToSuggestion() {
        Tag t = new Tag(); t.setName("Rust"); t.setOwnerId(OWNER); tagMapper.insert(t);
        String articleId = "a1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, OWNER, List.of("Rust", "某新概念"));
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }

    @Test
    void applyIsIdempotent() {
        Tag t = new Tag(); t.setName("LLM"); t.setOwnerId(OWNER); tagMapper.insert(t);
        String articleId = "a2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, OWNER, List.of("LLM", "新X"));
        classifier.apply(articleId, OWNER, List.of("LLM", "新X"));
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }

    @Test
    void hitOnlyMatchesWithinOwnerPrivatePool() {
        // 同名标签属于另一个用户:不应被链接,应进待确认建议(私有标签池隔离)。
        Tag others = new Tag(); others.setName("Kafka"); others.setOwnerId("x".repeat(32));
        tagMapper.insert(others);
        String articleId = "a5aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, OWNER, List.of("Kafka"));
        // 没链到他人的同名标签
        assertThat(articleTagMapper.selectList(null)).isEmpty();
        // 反而进了本文的待确认建议
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }

    @Test
    void mergedTagRedirectsToTarget() {
        Tag from = new Tag(); from.setName("旧名" + java.util.UUID.randomUUID());
        from.setOwnerId(OWNER); from.setArchived(true); tagMapper.insert(from);
        Tag to = new Tag(); to.setName("新名" + java.util.UUID.randomUUID()); to.setOwnerId(OWNER); tagMapper.insert(to);
        TagMerge m = new TagMerge(); m.setFromTagId(from.getId()); m.setToTagId(to.getId());
        tagMergeMapper.insert(m);

        String articleId = "a3aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        // 模型给出已合并的旧标签名 -> 应改投到 to,且来源为 ai
        classifier.apply(articleId, OWNER, List.of(from.getName()));

        var links = articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId));
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getTagId()).isEqualTo(to.getId());
        assertThat(links.get(0).getSource()).isEqualTo("ai");
    }

    @Test
    void archivedTagWithoutRedirectIsSkipped() {
        Tag archived = new Tag(); archived.setName("已归档" + java.util.UUID.randomUUID());
        archived.setOwnerId(OWNER); archived.setArchived(true); tagMapper.insert(archived);

        String articleId = "a4aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, OWNER, List.of(archived.getName()));

        // 归档且无重定向 -> 既不建链接,也不进建议
        assertThat(articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId))).isEmpty();
    }
}
