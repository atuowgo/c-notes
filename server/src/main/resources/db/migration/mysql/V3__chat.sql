-- V3:深聊（Deep Chat）——会话与消息持久化。
CREATE TABLE chat_session (
    id          CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    article_id  CHAR(32)              DEFAULT NULL COMMENT '锚定文章,可空',
    title       VARCHAR(255)          DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='深聊会话';

CREATE TABLE chat_message (
    id          CHAR(32)    NOT NULL COMMENT '32位UUID hex',
    session_id  CHAR(32)    NOT NULL,
    role        VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content     TEXT                 DEFAULT NULL,
    sources     TEXT                 DEFAULT NULL COMMENT '来源标签(JSON 文本,应用层序列化)',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='深聊消息';
