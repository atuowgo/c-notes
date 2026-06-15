package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import com.cnotes.tag.TagClassifier;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArticleProcessor {

    private final ArticleMapper articleMapper;
    private final ArticleOrganizer organizer;
    private final TagClassifier tagClassifier;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(Article a) {
        try {
            OrganizeResult r = organizer.organize(a.getTitle(), a.getContent(), tagClassifier.allowedTagNames());
            a.setSummary(r.summary());
            a.setKeyPoints(objectMapper.writeValueAsString(r.keyPoints()));
            tagClassifier.apply(a.getId(), r.tags());
            a.setStatus("done");
            a.setProcessedAt(LocalDateTime.now());
            a.setLastError(null);
            articleMapper.updateById(a);
        } catch (Exception e) {
            throw new RuntimeException("organize failed", e);  // 退避在 Worker 层(Task 7)
        }
    }
}
