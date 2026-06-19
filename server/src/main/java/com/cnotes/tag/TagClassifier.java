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
    private final TagMergeMapper tagMergeMapper;

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

        // 2) 本文已有的 article_tag(tagId 去重种子;含用户钉选,保留不动)
        Set<String> linkedTagIds = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId)).stream()
            .map(ArticleTag::getTagId).collect(Collectors.toCollection(HashSet::new));

        // 3) 本文已有的待确认标签名(name 去重种子)
        Set<String> suggestedNames = suggestionMapper.selectList(
                Wrappers.<TagSuggestion>lambdaQuery().eq(TagSuggestion::getArticleId, articleId)).stream()
            .map(TagSuggestion::getName).collect(Collectors.toCollection(HashSet::new));

        // 4) 合并重定向:from_tag_id -> to_tag_id;命中已合并(归档)的源簇时改投目标簇。
        Map<String, String> redirect = tagMergeMapper.selectList(null).stream()
            .collect(Collectors.toMap(TagMerge::getFromTagId, TagMerge::getToTagId, (a, b) -> a));

        for (String name : modelTags) {
            Tag tag = tagByName.get(name);
            if (tag != null) {
                Tag target = resolve(tag, redirect);
                if (target == null) continue;                 // 重定向断链或目标缺失,跳过
                if (Boolean.TRUE.equals(target.getArchived())) continue;   // 仍归档(且无重定向)则不应用
                if (linkedTagIds.add(target.getId())) {   // add 返回 false=已存在(库里或本批),跳过 -> 幂等
                    ArticleTag at = new ArticleTag();
                    at.setArticleId(articleId); at.setTagId(target.getId());
                    at.setSource("ai");                   // 自动归类来源
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

    /** 顺着 tag_merge 链把已合并的源簇解析到最终目标簇(最多跟随几跳,防环)。 */
    private Tag resolve(Tag tag, Map<String, String> redirect) {
        Tag cur = tag;
        for (int i = 0; i < 8 && redirect.containsKey(cur.getId()); i++) {
            String toId = redirect.get(cur.getId());
            cur = tagMapper.selectById(toId);
            if (cur == null) return null;
        }
        return cur;
    }
}
