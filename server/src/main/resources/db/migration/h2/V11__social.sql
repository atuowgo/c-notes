-- 多用户阶段 4:社交互动——点赞 + 评论 + 关注 + 公开批注 + 通知。

ALTER TABLE note ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
CREATE INDEX idx_note_article_vis ON note (article_id, visibility);

CREATE TABLE article_like (
    id          CHAR(32) NOT NULL,
    user_id     CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_like (user_id, article_id),
    KEY idx_like_article (article_id)
);

CREATE TABLE comment (
    id          CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    author_id   CHAR(32) NOT NULL,
    parent_id   CHAR(32)          DEFAULT NULL,
    body        TEXT     NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comment_article (article_id),
    KEY idx_comment_parent (parent_id)
);

CREATE TABLE follow (
    id          CHAR(32) NOT NULL,
    follower_id CHAR(32) NOT NULL,
    followee_id CHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (follower_id, followee_id),
    KEY idx_follow_followee (followee_id)
);

CREATE TABLE notification (
    id          CHAR(32)    NOT NULL,
    user_id     CHAR(32)    NOT NULL,
    type        VARCHAR(20) NOT NULL,
    actor_id    CHAR(32)    NOT NULL,
    article_id  CHAR(32)             DEFAULT NULL,
    comment_id  CHAR(32)             DEFAULT NULL,
    is_read     TINYINT(1)  NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notif_user (user_id, is_read)
);
