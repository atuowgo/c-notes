-- db/migration/mysql/V1__init_schema.sql
CREATE TABLE article (
    id              CHAR(32)      NOT NULL COMMENT '32位UUID hex',
    url             VARCHAR(2048) NOT NULL,
    url_hash        CHAR(32)      NOT NULL COMMENT 'MD5(url) hex',
    title           VARCHAR(512)           DEFAULT NULL,
    author          VARCHAR(256)           DEFAULT NULL,
    source_type     VARCHAR(32)   NOT NULL DEFAULT 'browser',
    content         LONGTEXT               DEFAULT NULL COMMENT '正文Markdown',
    summary         TEXT                   DEFAULT NULL,
    key_points      TEXT                   DEFAULT NULL COMMENT 'JSON数组字符串,应用层(反)序列化',
    status          VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/done/failed',
    extract_method  VARCHAR(32)            DEFAULT NULL,
    retry_count     INT           NOT NULL DEFAULT 0,
    next_retry_time DATETIME               DEFAULT NULL,
    last_error      VARCHAR(1024)          DEFAULT NULL,
    processed_at    DATETIME               DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_hash (url_hash),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章';

CREATE TABLE tag (
    id          CHAR(32)    NOT NULL,
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(256)         DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签';

CREATE TABLE article_tag (
    id          CHAR(32)     NOT NULL,
    article_id  CHAR(32)     NOT NULL,
    tag_id      CHAR(32)     NOT NULL,
    confidence  DECIMAL(4,3)          DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_tag (article_id, tag_id),
    KEY idx_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-标签';

CREATE TABLE tag_suggestion (
    id          CHAR(32)     NOT NULL,
    article_id  CHAR(32)     NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    confidence  DECIMAL(4,3)          DEFAULT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/rejected',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_name (article_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待确认标签';

CREATE TABLE note (
    id          CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    quote       TEXT     NOT NULL,
    thought     TEXT              DEFAULT NULL,
    anchor      TEXT              DEFAULT NULL COMMENT '正文定位 selector+offset(JSON 文本,应用层序列化)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_id (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='划线/想法';
