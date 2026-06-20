-- 多用户阶段 4:社交互动——点赞 + 评论 + 关注 + 公开批注 + 通知。

-- 公开批注:想法加可见性。存量想法一律私有(保持单租户/前三阶段行为)。
ALTER TABLE note ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
    COMMENT 'PRIVATE 仅自己 / PUBLIC 公开批注(他人文章上,所有人可见)';
CREATE INDEX idx_note_article_vis ON note (article_id, visibility);

-- 点赞
CREATE TABLE article_like (
    id          CHAR(32) NOT NULL,
    user_id     CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_like (user_id, article_id),
    KEY idx_like_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞';

-- 线程式评论(楼中楼一层:parent_id 指向顶层评论)
CREATE TABLE comment (
    id          CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    author_id   CHAR(32) NOT NULL,
    parent_id   CHAR(32)          DEFAULT NULL COMMENT '回复的顶层评论 id;NULL 为顶层',
    body        TEXT     NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comment_article (article_id),
    KEY idx_comment_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

-- 关注
CREATE TABLE follow (
    id          CHAR(32) NOT NULL,
    follower_id CHAR(32) NOT NULL COMMENT '关注者',
    followee_id CHAR(32) NOT NULL COMMENT '被关注者',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (follower_id, followee_id),
    KEY idx_follow_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注';

-- 通知(同步写入:点赞/评论/回复/关注/批注 触达内容所有者或被回复者)
CREATE TABLE notification (
    id          CHAR(32)    NOT NULL,
    user_id     CHAR(32)    NOT NULL COMMENT '接收者',
    type        VARCHAR(20) NOT NULL COMMENT 'LIKE/COMMENT/REPLY/FOLLOW/ANNOTATION',
    actor_id    CHAR(32)    NOT NULL COMMENT '触发者',
    article_id  CHAR(32)             DEFAULT NULL,
    comment_id  CHAR(32)             DEFAULT NULL,
    is_read     TINYINT(1)  NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notif_user (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知';
