-- V3:深聊（Deep Chat）——会话与消息持久化。
CREATE TABLE chat_session (
    id          CHAR(32)     NOT NULL,
    article_id  CHAR(32)              DEFAULT NULL,
    title       VARCHAR(255)          DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article (article_id)
);

CREATE TABLE chat_message (
    id          CHAR(32)    NOT NULL,
    session_id  CHAR(32)    NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     TEXT                 DEFAULT NULL,
    sources     TEXT                 DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session (session_id)
);
