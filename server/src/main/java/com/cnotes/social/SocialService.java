package com.cnotes.social;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.ShareLevel;
import com.cnotes.auth.UserContext;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.mapper.UserMapper;
import com.cnotes.note.dto.NoteAnchor;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import com.cnotes.share.ShareService;
import com.cnotes.social.dto.AnnotationRequest;
import com.cnotes.social.dto.CommentDto;
import com.cnotes.social.dto.PublicAnnotationDto;
import com.cnotes.social.entity.ArticleLike;
import com.cnotes.social.entity.Comment;
import com.cnotes.social.entity.Follow;
import com.cnotes.social.mapper.ArticleLikeMapper;
import com.cnotes.social.mapper.CommentMapper;
import com.cnotes.social.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社交互动:点赞 / 评论 / 公开批注 / 关注。
 * 能力门槛按文章生效分享级别(点赞=READ_ONLY、评论=COMMENTABLE、批注=ANNOTATABLE);写操作要求真实登录。
 * 触达内容所有者 / 被回复者的通知同步写入。
 */
@Service
@RequiredArgsConstructor
public class SocialService {

    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper likeMapper;
    private final CommentMapper commentMapper;
    private final FollowMapper followMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final ShareService shareService;
    private final NotificationService notifications;
    private final ObjectMapper om;

    /* ---------- 点赞 ---------- */

    @Transactional
    public void like(String articleId) {
        String me = requireLogin();
        Article a = loadAllowed(articleId, ShareLevel.READ_ONLY);
        if (me.equals(a.getOwnerId()) || isLiked(me, articleId)) return;
        ArticleLike l = new ArticleLike();
        l.setUserId(me);
        l.setArticleId(articleId);
        try {
            likeMapper.insert(l);
            notifications.notify(a.getOwnerId(), "LIKE", me, articleId, null);
        } catch (DuplicateKeyException ignore) { /* 幂等 */ }
    }

    @Transactional
    public void unlike(String articleId) {
        String me = requireLogin();
        likeMapper.delete(Wrappers.<ArticleLike>lambdaQuery()
            .eq(ArticleLike::getUserId, me).eq(ArticleLike::getArticleId, articleId));
    }

    public long likeCount(String articleId) {
        return likeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery().eq(ArticleLike::getArticleId, articleId));
    }

    public boolean isLiked(String userId, String articleId) {
        if (userId == null) return false;
        return likeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery()
            .eq(ArticleLike::getUserId, userId).eq(ArticleLike::getArticleId, articleId)) > 0;
    }

    /* ---------- 评论 ---------- */

    @Transactional
    public CommentDto addComment(String articleId, String body, String parentId) {
        String me = requireLogin();
        if (body == null || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论不能为空");
        Article a = loadAllowed(articleId, ShareLevel.COMMENTABLE);

        Comment c = new Comment();
        c.setArticleId(articleId);
        c.setAuthorId(me);
        c.setBody(body.trim());
        // 楼中楼一层:回复一律挂到顶层评论
        if (parentId != null && !parentId.isBlank()) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent != null && parent.getArticleId().equals(articleId)) {
                c.setParentId(parent.getParentId() == null ? parent.getId() : parent.getParentId());
                notifications.notify(parent.getAuthorId(), "REPLY", me, articleId, null);
            }
        }
        commentMapper.insert(c);
        notifications.notify(a.getOwnerId(), "COMMENT", me, articleId, c.getId());

        Map<String, User> u = usersById(Set.of(me));
        return toCommentDto(c, a.getOwnerId(), u, me);
    }

    public List<CommentDto> listComments(String articleId) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) return List.of();
        List<Comment> rows = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
            .eq(Comment::getArticleId, articleId).orderByAsc(Comment::getCreateTime));
        if (rows.isEmpty()) return List.of();
        Map<String, User> u = usersById(rows.stream().map(Comment::getAuthorId).collect(Collectors.toSet()));
        String me = UserContext.currentRaw();
        return rows.stream().map(c -> toCommentDto(c, a.getOwnerId(), u, me)).toList();
    }

    public long commentCount(String articleId) {
        return commentMapper.selectCount(Wrappers.<Comment>lambdaQuery().eq(Comment::getArticleId, articleId));
    }

    @Transactional
    public boolean deleteComment(String id) {
        String me = requireLogin();
        Comment c = commentMapper.selectById(id);
        if (c == null) return false;
        if (!me.equals(c.getAuthorId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return commentMapper.deleteById(id) > 0;
    }

    /* ---------- 公开批注 ---------- */

    @Transactional
    public PublicAnnotationDto addAnnotation(String articleId, AnnotationRequest req) {
        String me = requireLogin();
        Article a = loadAllowed(articleId, ShareLevel.ANNOTATABLE);
        Note n = new Note();
        n.setOwnerId(me);
        n.setArticleId(articleId);
        n.setQuote(req.getQuote());
        n.setThought(req.getThought());
        n.setAnchor(writeAnchor(req.getAnchor()));
        n.setVisibility("PUBLIC");
        noteMapper.insert(n);
        notifications.notify(a.getOwnerId(), "ANNOTATION", me, articleId, null);
        return toAnnotationDto(n, usersById(Set.of(me)), me);
    }

    public List<PublicAnnotationDto> listAnnotations(String articleId) {
        List<Note> rows = noteMapper.selectList(Wrappers.<Note>lambdaQuery()
            .eq(Note::getArticleId, articleId).eq(Note::getVisibility, "PUBLIC")
            .orderByAsc(Note::getCreateTime));
        if (rows.isEmpty()) return List.of();
        Map<String, User> u = usersById(rows.stream().map(Note::getOwnerId).collect(Collectors.toSet()));
        String me = UserContext.currentRaw();
        return rows.stream().map(n -> toAnnotationDto(n, u, me)).toList();
    }

    /* ---------- 关注 ---------- */

    @Transactional
    public void follow(String followeeId) {
        String me = requireLogin();
        if (me.equals(followeeId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能关注自己");
        if (userMapper.selectById(followeeId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (isFollowing(me, followeeId)) return;
        Follow f = new Follow();
        f.setFollowerId(me);
        f.setFolloweeId(followeeId);
        try {
            followMapper.insert(f);
            notifications.notify(followeeId, "FOLLOW", me, null, null);
        } catch (DuplicateKeyException ignore) { /* 幂等 */ }
    }

    @Transactional
    public void unfollow(String followeeId) {
        String me = requireLogin();
        followMapper.delete(Wrappers.<Follow>lambdaQuery()
            .eq(Follow::getFollowerId, me).eq(Follow::getFolloweeId, followeeId));
    }

    public boolean isFollowing(String followerId, String followeeId) {
        if (followerId == null) return false;
        return followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
            .eq(Follow::getFollowerId, followerId).eq(Follow::getFolloweeId, followeeId)) > 0;
    }

    public long followerCount(String userId) {
        return followMapper.selectCount(Wrappers.<Follow>lambdaQuery().eq(Follow::getFolloweeId, userId));
    }

    public long followingCount(String userId) {
        return followMapper.selectCount(Wrappers.<Follow>lambdaQuery().eq(Follow::getFollowerId, userId));
    }

    /** 我关注的用户 id 集合(供广场关注流)。 */
    public List<String> followeeIds(String followerId) {
        if (followerId == null) return List.of();
        return followMapper.selectList(Wrappers.<Follow>lambdaQuery().eq(Follow::getFollowerId, followerId))
            .stream().map(Follow::getFolloweeId).toList();
    }

    /* ---------- 内部 ---------- */

    private Article loadAllowed(String articleId, ShareLevel required) {
        Article a = articleMapper.selectById(articleId);
        if (a == null || !shareService.publiclyVisible(a)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!shareService.effectiveLevel(a).atLeast(required)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该文章未开放此操作");
        }
        return a;
    }

    private Map<String, User> usersById(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private CommentDto toCommentDto(Comment c, String articleOwnerId, Map<String, User> users, String me) {
        CommentDto d = new CommentDto();
        d.setId(c.getId());
        d.setArticleId(c.getArticleId());
        d.setParentId(c.getParentId());
        d.setBody(c.getBody());
        d.setAuthorId(c.getAuthorId());
        User a = users.get(c.getAuthorId());
        d.setAuthorNickname(a == null ? null : a.getNickname());
        d.setAuthorAvatarUrl(a == null ? null : a.getAvatarUrl());
        d.setByArticleAuthor(c.getAuthorId().equals(articleOwnerId));
        d.setMine(me != null && me.equals(c.getAuthorId()));
        d.setCreateTime(c.getCreateTime());
        return d;
    }

    private PublicAnnotationDto toAnnotationDto(Note n, Map<String, User> users, String me) {
        PublicAnnotationDto d = new PublicAnnotationDto();
        d.setId(n.getId());
        d.setQuote(n.getQuote());
        d.setThought(n.getThought());
        d.setAnchor(readAnchor(n.getAnchor()));
        d.setAuthorId(n.getOwnerId());
        User a = users.get(n.getOwnerId());
        d.setAuthorNickname(a == null ? null : a.getNickname());
        d.setMine(me != null && me.equals(n.getOwnerId()));
        d.setCreateTime(n.getCreateTime());
        return d;
    }

    private static String requireLogin() {
        String me = UserContext.currentRaw();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return me;
    }

    private String writeAnchor(NoteAnchor a) {
        if (a == null) return null;
        try { return om.writeValueAsString(a); } catch (Exception e) { return null; }
    }

    private NoteAnchor readAnchor(String json) {
        if (json == null || json.isBlank()) return null;
        try { return om.readValue(json, NoteAnchor.class); } catch (Exception e) { return null; }
    }
}
