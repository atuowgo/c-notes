package com.cnotes.chat;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.chat.vector.KnowledgeRetriever;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * P5:深聊上下文编排。给定锚定文章 + 批注 + 问题,assembler 织出 system 上下文——
 * 源1(本文摘要/要点/批注引文)+ 源2(知识网向量检索命中的簇综述);并记录实际启用了哪些源
 * (📄 本文 / 🕸 知识网),供消息 sources 标签。源3(🌐)由 ChatClient 通过 WebSearchTool
 * 自行决定调用,不在此预取。源2 检索用 @MockitoBean 隔离(其自身在 KnowledgeRetrieverTest 验证)。
 */
@SpringBootTest
@Transactional
class ChatContextAssemblerTest {

    @Autowired ChatContextAssembler assembler;
    @Autowired ArticleMapper articleMapper;
    @Autowired NoteMapper noteMapper;
    @MockitoBean KnowledgeRetriever knowledgeRetriever;

    private Article seedArticle() {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/a/" + h); a.setUrlHash(h);
        a.setTitle("红烧牛肉教程");
        a.setSummary("讲红烧牛肉的家常做法");
        a.setContent("正文若干……");
        a.setKeyPoints("[\"焯水去腥\",\"小火慢炖\"]");
        a.setStatus("done");
        articleMapper.insert(a);
        return a;
    }

    private void seedNote(String articleId, String quote, String thought) {
        Note n = new Note();
        n.setArticleId(articleId); n.setQuote(quote); n.setThought(thought);
        noteMapper.insert(n);
    }

    @Test
    void assemblesSource1AndSource2WithSourceTags() {
        Article a = seedArticle();
        seedNote(a.getId(), "小火慢炖", "关键在火候");
        seedNote(a.getId(), "焯水去腥", "去血水很重要");
        when(knowledgeRetriever.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeRetriever.Hit("cook", "烹饪", "炖肉技巧综述", 0.9)));

        var ctx = assembler.assemble(a.getId(), "怎么做更入味");

        assertThat(ctx.systemText()).contains("讲红烧牛肉的家常做法");  // 源1 摘要
        assertThat(ctx.systemText()).contains("焯水去腥");              // 源1 要点
        assertThat(ctx.systemText()).contains("小火慢炖");
        assertThat(ctx.systemText()).contains("关键在火候");            // 源1 批注想法
        assertThat(ctx.systemText()).contains("炖肉技巧综述");          // 源2 簇综述
        assertThat(ctx.systemText()).contains("烹饪");                  // 源2 簇名
        assertThat(ctx.sources()).contains("📄", "🕸");
    }

    @Test
    void seedNoteAddsThoughtSourceForAsk() {
        Article a = seedArticle();
        seedNote(a.getId(), "小火慢炖", "关键在火候");
        when(knowledgeRetriever.retrieve(anyString(), anyInt())).thenReturn(List.of());
        // 取回刚插入的想法 id
        Note seed = noteMapper.selectList(null).stream()
            .filter(n -> "关键在火候".equals(n.getThought())).findFirst().orElseThrow();

        var ctx = assembler.assemble(a.getId(), "基于这条想法继续聊", seed.getId());

        assertThat(ctx.sources()).contains("💭", "📄");
        assertThat(ctx.systemText()).contains("本次提问的起点");
        assertThat(ctx.systemText()).contains("关键在火候");
    }

    @Test
    void missingArticleAndNoKnowledgeYieldsNoSources() {
        when(knowledgeRetriever.retrieve(anyString(), anyInt())).thenReturn(List.of());

        var ctx = assembler.assemble("0".repeat(32), "随便问问");

        assertThat(ctx.sources()).isEmpty();
    }

    @Test
    void source1WithoutKnowledgeTagsOnlyArticle() {
        Article a = seedArticle();
        when(knowledgeRetriever.retrieve(anyString(), anyInt())).thenReturn(List.of());

        var ctx = assembler.assemble(a.getId(), "问题");

        assertThat(ctx.sources()).containsExactly("📄");
        assertThat(ctx.systemText()).contains("红烧牛肉教程");
    }
}
