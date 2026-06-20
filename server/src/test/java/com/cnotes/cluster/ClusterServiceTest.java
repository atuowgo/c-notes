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

import com.cnotes.chat.vector.ClusterIndexer;
import com.cnotes.tag.mapper.TagMergeMapper;
import com.cnotes.tag.entity.TagMerge;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired TagMergeMapper tagMergeMapper;
    @MockitoBean ClusterSummarizer summarizer;
    @MockitoBean ClusterSuggester suggester;
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

    // 无登录上下文下当前用户回退系统用户;簇(标签)须归属系统用户才可见/可操作。
    private static final String OWNER = com.cnotes.auth.entity.User.SYSTEM_ID;

    @Test
    void listDetailAndStalenessThenRegenerate() {
        Tag tag = new Tag(); tag.setName("知识网测试簇-" + java.util.UUID.randomUUID());
        tag.setOwnerId(OWNER);
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
        tag.setOwnerId(OWNER);
        tagMapper.insert(tag);
        link(seedDoneArticle("独苗"), tag.getId());
        // 仅 1 篇 < min(2),不应进入待写综述
        assertThat(clusterService.staleClusterTagIds()).doesNotContain(tag.getId());
    }

    private Tag newTag(String label) {
        Tag t = new Tag(); t.setName(label + "-" + java.util.UUID.randomUUID());
        t.setOwnerId(OWNER);
        tagMapper.insert(t);
        return t;
    }

    @Test
    void moveArticleMovesMembershipToTarget() {
        Tag from = newTag("源簇");
        Tag to = newTag("目标簇");
        String a = seedDoneArticle("被移文章");
        link(a, from.getId());

        var target = clusterService.moveArticle(from.getId(), a, to.getId());

        assertThat(target.getId()).isEqualTo(to.getId());
        assertThat(target.getArticles()).extracting("id").contains(a);
        assertThat(clusterService.detail(from.getId()).getArticles()).isEmpty();
    }

    @Test
    void mergeRetagsArchivesSourceAndWritesMergeRow() {
        Tag from = newTag("待并源簇");
        Tag to = newTag("并入目标簇");
        String a1 = seedDoneArticle("甲"); String a2 = seedDoneArticle("乙");
        link(a1, from.getId());
        link(a2, to.getId());

        var target = clusterService.merge(from.getId(), to.getId());

        // 成员合并到 to
        assertThat(target.getArticles()).extracting("id").contains(a1, a2);
        // 源簇 article_tag 清空
        assertThat(articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getTagId, from.getId()))).isEmpty();
        // 源簇归档,列表隐藏
        assertThat(tagMapper.selectById(from.getId()).getArchived()).isTrue();
        assertThat(clusterService.listClusters()).extracting("id").doesNotContain(from.getId());
        // tag_merge 重定向行写入
        assertThat(tagMergeMapper.selectList(
            Wrappers.<TagMerge>lambdaQuery().eq(TagMerge::getFromTagId, from.getId()))).hasSize(1);
    }

    @Test
    void splitCreatesNewClusterWithArticles() {
        Tag source = newTag("大簇");
        String a1 = seedDoneArticle("拆1"); String a2 = seedDoneArticle("拆2");
        link(a1, source.getId()); link(a2, source.getId());

        String newName = "拆出的新簇-" + java.util.UUID.randomUUID();
        var created = clusterService.split(source.getId(), newName, List.of(a1));

        assertThat(created.getName()).isEqualTo(newName);
        assertThat(created.getArticles()).extracting("id").containsExactly(a1);
        // a1 从源簇移除,a2 仍在
        assertThat(clusterService.detail(source.getId()).getArticles())
            .extracting("id").containsExactly(a2);
    }

    @Test
    void acceptSuggestionCreatesCluster() {
        String a1 = seedDoneArticle("建议1"); String a2 = seedDoneArticle("建议2");
        String name = "采纳簇-" + java.util.UUID.randomUUID();

        var created = clusterService.acceptSuggestion(name, List.of(a1, a2));

        assertThat(created.getName()).isEqualTo(name);
        assertThat(created.getArticles()).extracting("id").contains(a1, a2);
    }

    @Test
    void suggestionsEmptyWhenTooFewArticles() {
        // <3 篇 done 时不调用 LLM,直接空
        assertThat(clusterService.suggestions()).isEmpty();
    }

    @Test
    void clustersAreIsolatedPerOwner() {
        // 别的用户的簇:当前(系统)用户列表看不到、详情按 404、写操作被拒。
        Tag others = new Tag(); others.setName("他人簇-" + java.util.UUID.randomUUID());
        others.setOwnerId("z".repeat(32));
        tagMapper.insert(others);
        String a = seedDoneArticle("他人文章");
        link(a, others.getId());

        assertThat(clusterService.listClusters()).extracting("id").doesNotContain(others.getId());
        assertThat(clusterService.detail(others.getId())).isNull();

        Tag mine = newTag("我的簇");
        link(seedDoneArticle("我的文章"), mine.getId());
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> clusterService.merge(others.getId(), mine.getId()));   // 不能动他人簇
    }
}
