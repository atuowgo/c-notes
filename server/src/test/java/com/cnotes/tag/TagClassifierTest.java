package com.cnotes.tag;

import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.*;
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
}
