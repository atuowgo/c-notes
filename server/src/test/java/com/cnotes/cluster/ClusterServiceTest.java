package com.cnotes.cluster;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ClusterServiceTest {

    @Autowired ClusterService clusterService;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleMapper articleMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @MockitoBean ClusterSummarizer summarizer;

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
    }

    @Test
    void singleArticleClusterIsNotStale() {
        Tag tag = new Tag(); tag.setName("单篇簇-" + java.util.UUID.randomUUID());
        tagMapper.insert(tag);
        link(seedDoneArticle("独苗"), tag.getId());
        // 仅 1 篇 < min(2),不应进入待写综述
        assertThat(clusterService.staleClusterTagIds()).doesNotContain(tag.getId());
    }
}
