package com.cnotes.cluster;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.entity.ClusterPreference;
import com.cnotes.cluster.mapper.ClusterPreferenceMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.cnotes.chat.vector.ClusterIndexer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ClusterServiceTest {

    @Autowired ClusterService clusterService;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleMapper articleMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired ClusterPreferenceMapper clusterPreferenceMapper;
    @MockitoBean ClusterSummarizer summarizer;
    @MockitoBean ClusterIndexer clusterIndexer;   // 隔离 Ark 网络:断言综述写入后触发向量索引

    private String seedDoneArticle(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/c/" + h); a.setUrlHash(h);
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
    void listDetailAndStalenessThenRegenerate() {
        Tag tag = new Tag(); tag.setName("知识网测试簇-" + java.util.UUID.randomUUID());
        tagMapper.insert(tag);
        String a1 = seedDoneArticle("文甲");
        String a2 = seedDoneArticle("文乙");
        link(a1, tag.getId());
        link(a2, tag.getId());

        // 列表:该簇 2 篇、暂无综述
        var card = clusterService.listClusters().stream()
            .filter(c -> c.getId().equals(tag.getId())).findFirst().orElseThrow();
        assertThat(card.getArticleCount()).isEqualTo(2);
        assertThat(card.isHasSummary()).isFalse();

        // 详情:2 个成员
        var detail = clusterService.detail(tag.getId());
        assertThat(detail.getArticleCount()).isEqualTo(2);
        assertThat(detail.getArticles()).hasSize(2);

        // 成员数达标且无综述 → stale
        assertThat(clusterService.staleClusterTagIds()).contains(tag.getId());

        // 重写:写入综述 + 记录成员数,不再 stale
        when(summarizer.summarize(any(), any())).thenReturn("织好的综述");
        clusterService.regenerate(tag.getId());

        Tag after = tagMapper.selectById(tag.getId());
        assertThat(after.getLivingSummary()).isEqualTo("织好的综述");
        assertThat(after.getSummaryMemberCount()).isEqualTo(2);
        assertThat(clusterService.staleClusterTagIds()).doesNotContain(tag.getId());
        // 综述写入后,簇被(重)索引进知识网向量库
        verify(clusterIndexer).index(tag.getId());
    }

    @Test
    void singleArticleClusterIsNotStale() {
        Tag tag = new Tag(); tag.setName("单篇簇-" + java.util.UUID.randomUUID());
        tagMapper.insert(tag);
        link(seedDoneArticle("独苗"), tag.getId());
        // 仅 1 篇 < min(2),不应进入待写综述
        assertThat(clusterService.staleClusterTagIds()).doesNotContain(tag.getId());
    }

    @Test
    void mergeRetagsAllAndDeletesSource() {
        Tag src = new Tag(); src.setName("m-src-" + java.util.UUID.randomUUID());
        Tag tgt = new Tag(); tgt.setName("m-tgt-" + java.util.UUID.randomUUID());
        tagMapper.insert(src); tagMapper.insert(tgt);
        String a1 = seedDoneArticle("m1"); String a2 = seedDoneArticle("m2");
        link(a1, src.getId()); link(a2, src.getId());
        // a1 已在目标簇 → 验证去重(不重复插入,只删源链接)
        link(a1, tgt.getId());

        var detail = clusterService.merge(src.getId(), tgt.getId());

        assertThat(detail.getId()).isEqualTo(tgt.getId());
        assertThat(detail.getArticleCount()).isEqualTo(2);   // a1 + a2
        // 源标签已删
        assertThat(tagMapper.selectById(src.getId())).isNull();
        // 源簇下不再有任何链接
        assertThat(articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
            .eq(ArticleTag::getTagId, src.getId()))).isEmpty();
        // a1 在目标簇无重复(仅 1 条)
        assertThat(articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
            .eq(ArticleTag::getArticleId, a1).eq(ArticleTag::getTagId, tgt.getId()))).hasSize(1);
        // 纠偏审计落库
        assertThat(clusterPreferenceMapper.selectCount(Wrappers.<ClusterPreference>lambdaQuery()
            .eq(ClusterPreference::getAction, "merge"))).isPositive();
    }

    @Test
    void splitCreatesNewTagAndRetagsSelected() {
        Tag src = new Tag(); src.setName("s-src-" + java.util.UUID.randomUUID());
        tagMapper.insert(src);
        String a1 = seedDoneArticle("s1"); String a2 = seedDoneArticle("s2"); String a3 = seedDoneArticle("s3");
        link(a1, src.getId()); link(a2, src.getId()); link(a3, src.getId());
        String newName = "新拆簇-" + java.util.UUID.randomUUID();

        var detail = clusterService.split(src.getId(), List.of(a1, a2), newName);

        assertThat(detail.getName()).isEqualTo(newName);
        assertThat(detail.getArticleCount()).isEqualTo(2);   // 新簇 2 篇
        // 源簇剩 1 篇(a3)
        assertThat(clusterService.detail(src.getId()).getArticleCount()).isEqualTo(1);
        // 审计
        assertThat(clusterPreferenceMapper.selectCount(Wrappers.<ClusterPreference>lambdaQuery()
            .eq(ClusterPreference::getAction, "split"))).isPositive();
    }

    @Test
    void splitDuplicateNameThrows() {
        Tag src = new Tag(); src.setName("dup-src-" + java.util.UUID.randomUUID());
        tagMapper.insert(src);
        String dup = "重名簇-" + java.util.UUID.randomUUID();
        Tag existing = new Tag(); existing.setName(dup); tagMapper.insert(existing);
        String a1 = seedDoneArticle("d1"); link(a1, src.getId());

        assertThatThrownBy(() -> clusterService.split(src.getId(), List.of(a1), dup))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("簇名已存在");
    }

    @Test
    void moveRelocatesSingleArticle() {
        Tag src = new Tag(); src.setName("mv-src-" + java.util.UUID.randomUUID());
        Tag tgt = new Tag(); tgt.setName("mv-tgt-" + java.util.UUID.randomUUID());
        tagMapper.insert(src); tagMapper.insert(tgt);
        String a1 = seedDoneArticle("mv1"); String a2 = seedDoneArticle("mv2");
        link(a1, src.getId()); link(a2, src.getId());

        var detail = clusterService.move(src.getId(), a1, tgt.getId());

        // 返回刷新后的源簇:剩 a2
        assertThat(detail.getId()).isEqualTo(src.getId());
        assertThat(detail.getArticleCount()).isEqualTo(1);
        // 目标簇得到 a1
        assertThat(clusterService.detail(tgt.getId()).getArticleCount()).isEqualTo(1);
        assertThat(clusterPreferenceMapper.selectCount(Wrappers.<ClusterPreference>lambdaQuery()
            .eq(ClusterPreference::getAction, "move"))).isPositive();
    }
}
