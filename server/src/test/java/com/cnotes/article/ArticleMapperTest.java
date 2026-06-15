package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArticleMapperTest {

    @Autowired ArticleMapper mapper;

    @Test
    void insertAssignsUuidAndTimestamps() {
        Article a = new Article();
        a.setUrl("https://e.com/x"); a.setUrlHash("00000000000000000000000000000001");
        a.setStatus("pending");
        mapper.insert(a);
        assertThat(a.getId()).hasSize(32);
        Article got = mapper.selectById(a.getId());
        assertThat(got.getCreateTime()).isNotNull();
        assertThat(got.getUpdateTime()).isNotNull();
    }

    @Test
    void updateRefreshesUpdateTime() throws Exception {
        Article a = new Article();
        a.setUrl("https://e.com/y"); a.setUrlHash("00000000000000000000000000000002");
        a.setStatus("pending");
        mapper.insert(a);
        var before = mapper.selectById(a.getId()).getUpdateTime();
        Thread.sleep(20);
        Article upd = new Article();
        upd.setId(a.getId()); upd.setStatus("done");
        mapper.updateById(upd);   // 触发 updateFill
        assertThat(mapper.selectById(a.getId()).getUpdateTime()).isAfterOrEqualTo(before);
    }
}
