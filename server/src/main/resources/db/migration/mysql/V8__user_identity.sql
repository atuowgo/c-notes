-- 多用户阶段 1 地基:用户与三方登录身份。表名用 app_user 规避 MySQL/H2 保留字 user。
CREATE TABLE app_user (
    id                  CHAR(32)     NOT NULL COMMENT '32位UUID hex',
    email               VARCHAR(255)          DEFAULT NULL COMMENT '可空;三方未回传或 OTP 未上线时为空',
    nickname            VARCHAR(64)           DEFAULT NULL,
    avatar_url          VARCHAR(512)          DEFAULT NULL,
    default_share_level VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/READ_ONLY/BOOKMARKABLE/COLLECTABLE/ANNOTATABLE/COMMENTABLE',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE auth_identity (
    id           CHAR(32)     NOT NULL,
    user_id      CHAR(32)     NOT NULL,
    provider     VARCHAR(20)  NOT NULL COMMENT 'github/google/wechat;email 预留',
    provider_uid VARCHAR(128) NOT NULL COMMENT 'GitHub id / Google sub / 微信 openid',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_uid (provider, provider_uid),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三方登录身份';

-- 系统初始用户:承接多用户化之前的全部存量数据(单租户老数据回填到此用户),避免孤儿。
INSERT INTO app_user (id, nickname, default_share_level, create_time)
VALUES ('00000000000000000000000000000001', '我', 'PRIVATE', CURRENT_TIMESTAMP);
