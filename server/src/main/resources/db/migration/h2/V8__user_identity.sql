-- 多用户阶段 1 地基:用户与三方登录身份。表名 app_user 规避保留字。
CREATE TABLE app_user (
    id                  CHAR(32)     NOT NULL,
    email               VARCHAR(255)          DEFAULT NULL,
    nickname            VARCHAR(64)           DEFAULT NULL,
    avatar_url          VARCHAR(512)          DEFAULT NULL,
    default_share_level VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email)
);

CREATE TABLE auth_identity (
    id           CHAR(32)     NOT NULL,
    user_id      CHAR(32)     NOT NULL,
    provider     VARCHAR(20)  NOT NULL,
    provider_uid VARCHAR(128) NOT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_uid (provider, provider_uid),
    KEY idx_user (user_id)
);

INSERT INTO app_user (id, nickname, default_share_level, create_time)
VALUES ('00000000000000000000000000000001', '我', 'PRIVATE', CURRENT_TIMESTAMP);
