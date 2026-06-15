-- db/migration/h2/V1__init_schema.sql
CREATE TABLE article (
    id              CHAR(32)      NOT NULL,
    url             VARCHAR(2048) NOT NULL,
    url_hash        CHAR(32)      NOT NULL,
    title           VARCHAR(512)           DEFAULT NULL,
    author          VARCHAR(256)           DEFAULT NULL,
    source_type     VARCHAR(32)   NOT NULL DEFAULT 'browser',
    content         LONGTEXT               DEFAULT NULL,
    summary         TEXT                   DEFAULT NULL,
    key_points      TEXT                   DEFAULT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'pending',
    extract_method  VARCHAR(32)            DEFAULT NULL,
    retry_count     INT           NOT NULL DEFAULT 0,
    next_retry_time DATETIME               DEFAULT NULL,
    last_error      VARCHAR(1024)          DEFAULT NULL,
    processed_at    DATETIME               DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_hash (url_hash),
    KEY idx_status_retry (status, next_retry_time)
);

CREATE TABLE tag (
    id          CHAR(32)    NOT NULL,
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(256)         DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
);

CREATE TABLE article_tag (
    id          CHAR(32)     NOT NULL,
    article_id  CHAR(32)     NOT NULL,
    tag_id      CHAR(32)     NOT NULL,
    confidence  DECIMAL(4,3)          DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_tag (article_id, tag_id),
    KEY idx_tag_id (tag_id)
);

CREATE TABLE tag_suggestion (
    id          CHAR(32)     NOT NULL,
    article_id  CHAR(32)     NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    confidence  DECIMAL(4,3)          DEFAULT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_name (article_id, name)
);

CREATE TABLE note (
    id          CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    quote       TEXT     NOT NULL,
    thought     TEXT              DEFAULT NULL,
    anchor      JSON              DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_id (article_id)
);
