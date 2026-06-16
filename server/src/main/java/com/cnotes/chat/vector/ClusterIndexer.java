package com.cnotes.chat.vector;

import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.TagMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 知识网(源2)向量索引器:把某个主题簇(tag)的演进式综述 living_summary
 * 作为一条 Document 写入本地文件型 {@link SimpleVectorStore},并落盘。
 * 以 tagId 为 Document id,重复索引同一 tag 走「先删后加」覆盖,不产生重复。
 * 无综述的 tag 直接跳过。深聊检索时按 query 相似度召回最近的簇综述。
 */
@Component
public class ClusterIndexer {

    private final SimpleVectorStore vectorStore;
    private final TagMapper tagMapper;
    private final String path;

    public ClusterIndexer(SimpleVectorStore vectorStore,
                          TagMapper tagMapper,
                          @Value("${cnotes.vectorstore.path:./.data/vectorstore.json}") String path) {
        this.vectorStore = vectorStore;
        this.tagMapper = tagMapper;
        this.path = path;
    }

    public void index(String tagId) {
        Tag t = tagMapper.selectById(tagId);
        if (t == null || t.getLivingSummary() == null || t.getLivingSummary().isBlank()) {
            return;
        }
        // 以 tagId 为 Document id 覆盖,避免重复索引同一簇产生多条。
        try {
            vectorStore.delete(List.of(t.getId()));
        } catch (Exception ignore) {
            // 首次索引时 id 不存在,删除可能抛异常,忽略。
        }
        Document doc = Document.builder()
            .id(t.getId())
            .text(t.getLivingSummary())
            .metadata(Map.of(
                "tagId", t.getId(),
                "tagName", t.getName() == null ? "" : t.getName()))
            .build();
        vectorStore.add(List.of(doc));

        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        vectorStore.save(file);
    }
}
