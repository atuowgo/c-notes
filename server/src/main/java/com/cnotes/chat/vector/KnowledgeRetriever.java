package com.cnotes.chat.vector;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 源2 读路径:在本地文件型 {@link SimpleVectorStore}(由 {@link ClusterIndexer} 写入簇综述向量)上
 * 做相似度检索,把命中的簇综述映射成 {@link Hit}(tagId / tagName / 综述 / 相似度),供深聊把知识网
 * 上下文织入 system 提示。
 *
 * <p><b>优雅降级</b>:空查询、空库、检索异常一律返回空列表,源2 缺位不应使整轮 chat 失败。
 */
@Component
public class KnowledgeRetriever {

    private final SimpleVectorStore vectorStore;

    public KnowledgeRetriever(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 命中的一个簇:tagId / tagName / living_summary 正文 / 相似度分(可空)。 */
    public record Hit(String tagId, String tagName, String summary, Double score) {}

    /**
     * 按 query 相似度召回当前用户(ownerId)自己的簇综述。
     * 多用户隔离:SimpleVectorStore 是全库单例,这里超额召回后在内存按 metadata.ownerId 过滤,
     * 再截断到 topK——不依赖底层向量库是否支持 filterExpression,保证不会命中他人私有综述。
     */
    public List<Hit> retrieve(String query, int topK, String ownerId) {
        if (query == null || query.isBlank() || ownerId == null) return List.of();
        try {
            int fetch = Math.max(topK * 5, 50);   // 超额召回,留足过滤余量
            List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(fetch).build());
            if (docs == null) return List.of();
            return docs.stream()
                .filter(d -> ownerId.equals(str(d.getMetadata().get("ownerId"))))
                .limit(topK)
                .map(d -> new Hit(
                    str(d.getMetadata().get("tagId")),
                    str(d.getMetadata().get("tagName")),
                    d.getText(),
                    d.getScore()))
                .toList();
        } catch (Exception e) {
            return List.of();   // 优雅降级:源2 不可用不抛
        }
    }

    private static String str(Object o) {
        return o == null ? "" : Objects.toString(o);
    }
}
