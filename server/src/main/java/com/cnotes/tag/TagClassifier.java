package com.cnotes.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.tag.entity.*;
import com.cnotes.tag.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagClassifier {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagSuggestionMapper suggestionMapper;

    public List<String> allowedTagNames() {
        return tagMapper.selectList(null).stream().map(Tag::getName).toList();
    }

    @Transactional
    public void apply(String articleId, List<String> modelTags) {
        for (String name : modelTags) {
            Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
            if (tag != null) {
                if (articleTagMapper.selectCount(Wrappers.<ArticleTag>lambdaQuery()
                        .eq(ArticleTag::getArticleId, articleId)
                        .eq(ArticleTag::getTagId, tag.getId())) == 0) {
                    ArticleTag at = new ArticleTag();
                    at.setArticleId(articleId); at.setTagId(tag.getId());
                    articleTagMapper.insert(at);
                }
            } else {
                if (suggestionMapper.selectCount(Wrappers.<TagSuggestion>lambdaQuery()
                        .eq(TagSuggestion::getArticleId, articleId)
                        .eq(TagSuggestion::getName, name)) == 0) {
                    TagSuggestion s = new TagSuggestion();
                    s.setArticleId(articleId); s.setName(name); s.setStatus("pending");
                    suggestionMapper.insert(s);
                }
            }
        }
    }
}
