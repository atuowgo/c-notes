package com.cnotes.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.tag.dto.TagSuggestionDto;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.entity.TagSuggestion;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import com.cnotes.tag.mapper.TagSuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagSuggestionService {

    private final TagSuggestionMapper suggestionMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    /** 列出某文章的 pending 标签建议。 */
    public List<TagSuggestionDto> listPending(String articleId) {
        return suggestionMapper.selectList(
                Wrappers.<TagSuggestion>lambdaQuery()
                    .eq(TagSuggestion::getArticleId, articleId)
                    .eq(TagSuggestion::getStatus, "pending"))
            .stream().map(this::toDto).toList();
    }

    /**
     * 接受:若受控集里没有同名标签则新建;把文章链到该标签;把建议标记为 accepted。
     * 幂等:文章已有该标签链接时仅更新建议状态。
     */
    @Transactional
    public TagSuggestionDto accept(String suggestionId) {
        TagSuggestion s = getOrThrow(suggestionId);

        // 找到或新建受控标签
        Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, s.getName()));
        if (tag == null) {
            tag = new Tag();
            tag.setName(s.getName());
            tagMapper.insert(tag);
        }

        // 幂等链接 article_tag
        long exists = articleTagMapper.selectCount(
            Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, s.getArticleId())
                .eq(ArticleTag::getTagId, tag.getId()));
        if (exists == 0) {
            ArticleTag at = new ArticleTag();
            at.setArticleId(s.getArticleId());
            at.setTagId(tag.getId());
            at.setSource("user");
            articleTagMapper.insert(at);
        }

        s.setStatus("accepted");
        suggestionMapper.updateById(s);
        return toDto(s);
    }

    /** 拒绝:仅更新状态,不影响受控标签集。 */
    @Transactional
    public TagSuggestionDto reject(String suggestionId) {
        TagSuggestion s = getOrThrow(suggestionId);
        s.setStatus("rejected");
        suggestionMapper.updateById(s);
        return toDto(s);
    }

    private TagSuggestion getOrThrow(String id) {
        TagSuggestion s = suggestionMapper.selectById(id);
        if (s == null) throw new IllegalArgumentException("TagSuggestion not found: " + id);
        return s;
    }

    private TagSuggestionDto toDto(TagSuggestion s) {
        TagSuggestionDto d = new TagSuggestionDto();
        d.setId(s.getId());
        d.setArticleId(s.getArticleId());
        d.setName(s.getName());
        d.setConfidence(s.getConfidence());
        d.setStatus(s.getStatus());
        d.setCreateTime(s.getCreateTime());
        return d;
    }
}
