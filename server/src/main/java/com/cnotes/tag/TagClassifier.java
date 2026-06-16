package com.cnotes.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.tag.entity.*;
import com.cnotes.tag.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagClassifier {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagSuggestionMapper suggestionMapper;

    public List<String> allowedTagNames() {
        return tagMapper.selectList(null).stream().map(Tag::getName).toList();
    }

    /**
     * 把模型给的标签名落到该文章:命中受控标签 -> 建 article_tag 链接;未命中 -> 进待确认表。
     * 与逐名 selectOne/selectCount 的旧实现行为等价(命中归类、未命中建议、链接/建议幂等),
     * 但查询数与标签数解耦:固定 3 次查询(按名批量取标签、取本文已有链接、取本文已有建议),
     * 再用 Set.add 同时去重"库里已存在"与"本次入参重复",在内存里决策后插入。
     */
    @Transactional
    public void apply(String articleId, List<String> modelTags) {
        if (modelTags == null || modelTags.isEmpty()) return;

        // 1) 一次按名批量取命中的受控标签:name -> Tag(同名取其一,沿用旧 selectOne 语义)
        Map<String, Tag> tagByName = tagMapper.selectList(
                Wrappers.<Tag>lambdaQuery().in(Tag::getName, modelTags)).stream()
            .collect(Collectors.toMap(Tag::getName, Function.identity(), (a, b) -> a));

        // 2) 本文已有的 article_tag(tagId 去重种子)
        Set<String> linkedTagIds = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId)).stream()
            .map(ArticleTag::getTagId).collect(Collectors.toCollection(HashSet::new));

        // 3) 本文已有的待确认标签名(name 去重种子)
        Set<String> suggestedNames = suggestionMapper.selectList(
                Wrappers.<TagSuggestion>lambdaQuery().eq(TagSuggestion::getArticleId, articleId)).stream()
            .map(TagSuggestion::getName).collect(Collectors.toCollection(HashSet::new));

        for (String name : modelTags) {
            Tag tag = tagByName.get(name);
            if (tag != null) {
                if (linkedTagIds.add(tag.getId())) {   // add 返回 false=已存在(库里或本批),跳过 -> 幂等
                    ArticleTag at = new ArticleTag();
                    at.setArticleId(articleId); at.setTagId(tag.getId());
                    articleTagMapper.insert(at);
                }
            } else {
                if (suggestedNames.add(name)) {
                    TagSuggestion s = new TagSuggestion();
                    s.setArticleId(articleId); s.setName(name); s.setStatus("pending");
                    suggestionMapper.insert(s);
                }
            }
        }
    }
}
