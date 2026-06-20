package com.cnotes.social;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.UserContext;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.mapper.UserMapper;
import com.cnotes.social.dto.NotificationDto;
import com.cnotes.social.entity.Notification;
import com.cnotes.social.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 通知:写操作同步触达内容所有者 / 被回复者;不给自己发通知。 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;

    /** 记一条通知;recipient 为空或等于 actor(自己操作自己)则跳过。 */
    public void notify(String recipientId, String type, String actorId, String articleId, String commentId) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        Notification n = new Notification();
        n.setUserId(recipientId);
        n.setType(type);
        n.setActorId(actorId);
        n.setArticleId(articleId);
        n.setCommentId(commentId);
        n.setRead(false);
        notificationMapper.insert(n);
    }

    public long unreadCount() {
        String me = UserContext.currentRaw();
        if (me == null) return 0;
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
            .eq(Notification::getUserId, me).eq(Notification::getRead, false));
    }

    public List<NotificationDto> list() {
        String me = UserContext.currentRaw();
        if (me == null) return List.of();
        List<Notification> rows = notificationMapper.selectList(Wrappers.<Notification>lambdaQuery()
            .eq(Notification::getUserId, me).orderByDesc(Notification::getCreateTime).last("LIMIT 50"));
        if (rows.isEmpty()) return List.of();

        Set<String> actorIds = rows.stream().map(Notification::getActorId).collect(Collectors.toSet());
        Map<String, User> actors = actorIds.isEmpty() ? Map.of()
            : userMapper.selectBatchIds(actorIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Set<String> articleIds = rows.stream().map(Notification::getArticleId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> titles = articleIds.isEmpty() ? Map.of()
            : articleMapper.selectBatchIds(articleIds).stream()
                .filter(a -> a.getTitle() != null).collect(Collectors.toMap(Article::getId, Article::getTitle));

        return rows.stream().map(n -> {
            NotificationDto d = new NotificationDto();
            d.setId(n.getId());
            d.setType(n.getType());
            d.setActorId(n.getActorId());
            User a = actors.get(n.getActorId());
            d.setActorNickname(a == null ? null : a.getNickname());
            d.setActorAvatarUrl(a == null ? null : a.getAvatarUrl());
            d.setArticleId(n.getArticleId());
            d.setArticleTitle(n.getArticleId() == null ? null : titles.get(n.getArticleId()));
            d.setCommentId(n.getCommentId());
            d.setRead(Boolean.TRUE.equals(n.getRead()));
            d.setCreateTime(n.getCreateTime());
            return d;
        }).toList();
    }

    @Transactional
    public void markAllRead() {
        String me = UserContext.currentRaw();
        if (me == null) return;
        notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
            .eq(Notification::getUserId, me).eq(Notification::getRead, false)
            .set(Notification::getRead, true));
    }
}
