# MVP 后端骨架 实现计划 (Backend Spine)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 立起「个人知识炼金炉」的后端脊柱——收文入库(先入库后处理)、异步 Worker 出摘要/要点/标签、受控标签归类、收件箱读取接口——本地用 H2 即可跑通,上线切 MySQL。

**Architecture:** 单模块 Spring Boot 常驻服务,对外暴露 HTTP。`/api/collect` 收提交即写 `article(status=pending)` 立即返回;同进程 `@Scheduled` Worker 轮询 `pending`/到期 `failed` → 用 **Spring AI `ChatClient` 结构化输出**一次拿全 summary+key_points+tags → 受控标签入 `article_tag`、新标签入 `tag_suggestion` → 置 `done`;失败按指数退避。收/发逻辑分包,为将来多机预留。

**Tech Stack:** Gradle(Groovy DSL,**用 Gradle Wrapper `gradlew`**)、**Java 21**、**Spring Boot 4.1.x**、**Spring AI 2.0.0(`spring-ai-bom`,模型层直接用 `ChatClient`,不自建抽象)**、MyBatis-Plus(`mybatis-plus-spring-boot4-starter`)、Flyway(`flyway-core` + `flyway-mysql`)、**测试/调试用 H2(MySQL 兼容模式),上线切 MySQL 8**、Lombok、JUnit 5。

> ⚠️ **版本与环境提醒**
> - Spring AI 2.0.0 GA(2026-06-12)**强制 Spring Boot 4.0/4.1 + Java 21**,不能在 Boot 3.x 上加载。故本计划基线 = Boot 4.1 + Java 21(原 3.2/17 已升级)。
> - 各依赖**补丁版本号以 Spring AI 2.0 BOM / `start.spring.io` 实际对齐为准**;下方版本为撰写时的合理取值,执行时若解析失败请对齐 BOM。
> - 构建需访问 **Maven Central**。当前 Claude 沙箱网络白名单不含 Maven Central,**实际 `./gradlew build` 须在你本机或放开白名单的环境执行**;本会话只负责写计划与 git push(GitHub 可达)。
> - 模型 Provider 以 `spring-ai-starter-model-openai` 为占位示例,Spring AI 的意义就是**换 starter 不改 `ChatClient` 代码**;上线选定 Provider(如通义/OpenAI 等)并配 api-key 即可。
> - **若希望避免 Boot-4/Java-21 大跨步**,可退到 Spring AI 1.0.9 + Spring Boot 3.3(稳定组合),代价是用旧版 Spring AI;本计划按你"用 spring 2.0 GA"的明确要求选 2.0。

**约定(贯穿全程):**
- 建表规范:`id CHAR(32)` 物理主键(MyBatis-Plus `IdType.ASSIGN_UUID`,32 位无横线 UUID);长字段唯一键用 `xxx_hash CHAR(32)`;业务唯一索引齐全。
- 时间戳:**MySQL 生产脚本**保留标准 `create_time DEFAULT CURRENT_TIMESTAMP` + `update_time ... ON UPDATE CURRENT_TIMESTAMP`;**因 H2 不支持 `ON UPDATE CURRENT_TIMESTAMP`**,统一由 MyBatis-Plus `MetaObjectHandler` 在应用层补 `create_time`(insert)/`update_time`(insert+update),两库行为一致,MySQL 的 DDL 默认值作为非应用写入的兜底。这是对"DB 全权托管时间戳"老约定的**有意微调**,目的是让 H2 能跑测试。
- DDL 走 Flyway **按库分目录**:`classpath:db/migration/{vendor}`(`{vendor}` 解析为 `mysql` 或 `h2`),两套脚本。
- 包根 `com.cnotes`;收(`collect`)与发(`worker`)分包。
- DRY / YAGNI / TDD / 每个 Task 末尾提交一次。
- 先决条件:JDK 21、已安装 Gradle(用于首次生成 wrapper;之后一律 `./gradlew`)。测试用 H2 内存库,**无需 Docker**。

---

## Task 0:Gradle 工程骨架(含 gradlew)+ H2 测试基座

**Files:**
- Create: `server/build.gradle`
- Create: `server/settings.gradle`
- Create: `server/src/main/java/com/cnotes/CNotesApplication.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/test/resources/application.yml`(测试走 H2)
- Create: `server/src/test/java/com/cnotes/CNotesApplicationTests.java`
- Generate: `server/gradlew`、`server/gradlew.bat`、`server/gradle/wrapper/*`

**Step 1:生成 Gradle Wrapper**(用户要求下载 gradlew)

```bash
cd server
gradle wrapper --gradle-version 8.14   # 需本机已装 gradle;如未装:brew install gradle 或 sdkman
# 生成 gradlew / gradlew.bat / gradle/wrapper/gradle-wrapper.{jar,properties}
```
Expected: 出现 `server/gradlew` 及 `gradle/wrapper/` 文件。之后所有命令用 `./gradlew`。

**Step 2:写 `settings.gradle`**

```groovy
rootProject.name = 'server'
```

**Step 3:写 `build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.cnotes'
version = '0.0.1-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }   // Spring AI 2.0 / Boot 4 要求 Java 21
}

repositories { mavenCentral() }

ext {
    set('springAiVersion', '2.0.0')
    set('mybatisPlusVersion', '3.5.16')   // 含 spring-boot4-starter,Maven Central 已核实存在
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation "com.baomidou:mybatis-plus-spring-boot4-starter:${mybatisPlusVersion}"
    implementation 'org.springframework.ai:spring-ai-starter-model-openai'  // 模型层,Provider 可换
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    runtimeOnly 'com.mysql:mysql-connector-j'
    runtimeOnly 'com.h2database:h2'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}"
    }
}

tasks.named('test') { useJUnitPlatform() }
```

**Step 4:写主类**

```java
package com.cnotes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.cnotes.**.mapper")
public class CNotesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CNotesApplication.class, args);
    }
}
```

**Step 5:写主 `application.yml`**(生产 MySQL;Flyway 按库分目录)

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration/{vendor}   # 解析为 mysql / h2
    baseline-on-migrate: true
  datasource:
    url: jdbc:mysql://localhost:3306/cnotes?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}   # 上线配置;占位
mybatis-plus:
  global-config:
    db-config:
      id-type: assign_uuid
  configuration:
    map-underscore-to-camel-case: true
worker:
  poll-interval-ms: 5000
  max-retry: 5
  backoff-base-seconds: 30
```

**Step 6:写测试 `application.yml`**(H2,MySQL 兼容模式,无需 Docker/真实模型)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:cnotes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password: ''
    driver-class-name: org.h2.Driver
  flyway:
    locations: classpath:db/migration/{vendor}   # H2 → db/migration/h2
  ai:
    openai:
      api-key: test-dummy   # 防止 OpenAI 自动配置启动报错;测试用 stub/mock 模型,不发网络
```

**Step 7:写冒烟测试**

```java
package com.cnotes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CNotesApplicationTests {
    @Test
    void contextLoads() { }
}
```

**Step 8:跑测试验证基座**

Run: `cd server && ./gradlew test --tests "com.cnotes.CNotesApplicationTests"`
Expected: PASS(Spring 上下文起得来,H2 连得上;此时 `db/migration/h2` 为空,Flyway 空跑不报错)。

**Step 9:提交**

```bash
git add server/build.gradle server/settings.gradle server/gradlew server/gradlew.bat server/gradle server/src
git commit -m "chore: gradle + spring boot 4 + spring ai 2.0 骨架,h2 测试基座"
```

---

## Task 1:Flyway 建 5 张表(MySQL + H2 两套脚本)

**Files:**
- Create: `server/src/main/resources/db/migration/mysql/V1__init_schema.sql`
- Create: `server/src/main/resources/db/migration/h2/V1__init_schema.sql`
- Test: `server/src/test/java/com/cnotes/schema/SchemaMigrationTest.java`

**Step 1:写 MySQL 生产脚本**(标准原样,含 `ON UPDATE CURRENT_TIMESTAMP`)

```sql
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
    key_points      JSON                   DEFAULT NULL,
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
    anchor      JSON              DEFAULT NULL COMMENT '正文定位 selector+offset',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_id (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='划线/想法';
```

**Step 2:写 H2 脚本**(去掉 `ON UPDATE`、去掉 `ENGINE/CHARSET` 表选项;MODE=MySQL 下 `LONGTEXT`/`JSON` 可用;`update_time` 由应用层 MetaObjectHandler 维护)

```sql
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
    key_points      JSON                   DEFAULT NULL,
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
```

**Step 3:写迁移验证测试(先失败)**——只验证 5 表存在(时间戳自动更新移到 Task 2 经 mapper 验证)

```java
package com.cnotes.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchemaMigrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void allFiveTablesExist() {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE LOWER(table_name) IN " +
            "('article','tag','article_tag','tag_suggestion','note')", Integer.class);
        assertThat(n).isEqualTo(5);
    }
}
```

**Step 4:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.schema.SchemaMigrationTest"`
Expected: PASS(H2 上 5 表创建成功)。

**Step 5:提交**

```bash
git add server/src/main/resources/db/migration server/src/test/java/com/cnotes/schema
git commit -m "feat: flyway 建五表,mysql 与 h2 双脚本"
```

---

## Task 2:Article 实体 + Mapper + 时间戳自动填充

**Files:**
- Create: `server/src/main/java/com/cnotes/article/entity/Article.java`
- Create: `server/src/main/java/com/cnotes/article/mapper/ArticleMapper.java`
- Create: `server/src/main/java/com/cnotes/config/AutoFillHandler.java`
- Test: `server/src/test/java/com/cnotes/article/ArticleMapperTest.java`

**Step 1:写时间戳自动填充器**(替代 H2 缺失的 `ON UPDATE`,两库一致)

```java
package com.cnotes.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class AutoFillHandler implements MetaObjectHandler {
    @Override public void insertFill(MetaObject m) {
        strictInsertFill(m, "createTime", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
    @Override public void updateFill(MetaObject m) {
        strictUpdateFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

**Step 2:写实体**(`create_time`=INSERT 填充,`update_time`=INSERT_UPDATE 填充)

```java
package com.cnotes.article.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String url;
    private String urlHash;
    private String title;
    private String author;
    private String sourceType;
    private String content;
    private String summary;
    private String keyPoints;     // JSON 字符串,Service 层序列化
    private String status;
    private String extractMethod;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime processedAt;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

**Step 3:写 Mapper**

```java
package com.cnotes.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cnotes.article.entity.Article;

public interface ArticleMapper extends BaseMapper<Article> {
}
```

**Step 4:写测试(先失败)**——id 32 位、insert 填时间戳、update 刷新 `update_time`

```java
package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArticleMapperTest {

    @Autowired ArticleMapper mapper;

    @Test
    void insertAssignsUuidAndTimestamps() {
        Article a = new Article();
        a.setUrl("https://e.com/x"); a.setUrlHash("00000000000000000000000000000001");
        a.setStatus("pending");
        mapper.insert(a);
        assertThat(a.getId()).hasSize(32);
        Article got = mapper.selectById(a.getId());
        assertThat(got.getCreateTime()).isNotNull();
        assertThat(got.getUpdateTime()).isNotNull();
    }

    @Test
    void updateRefreshesUpdateTime() throws Exception {
        Article a = new Article();
        a.setUrl("https://e.com/y"); a.setUrlHash("00000000000000000000000000000002");
        a.setStatus("pending");
        mapper.insert(a);
        var before = mapper.selectById(a.getId()).getUpdateTime();
        Thread.sleep(20);
        Article upd = new Article();
        upd.setId(a.getId()); upd.setStatus("done");
        mapper.updateById(upd);   // 触发 updateFill
        assertThat(mapper.selectById(a.getId()).getUpdateTime()).isAfterOrEqualTo(before);
    }
}
```

**Step 5:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.article.ArticleMapperTest"`
Expected: PASS。

**Step 6:提交**

```bash
git add server/src/main/java/com/cnotes/article server/src/main/java/com/cnotes/config
git add server/src/test/java/com/cnotes/article/ArticleMapperTest.java
git commit -m "feat: article 实体/mapper + metaobjecthandler 时间戳自动填充"
```

---

## Task 3:`/api/collect` 入库(先入库后处理 + url_hash 幂等)

**Files:**
- Create: `server/src/main/java/com/cnotes/collect/dto/CollectRequest.java`
- Create: `server/src/main/java/com/cnotes/collect/CollectService.java`
- Create: `server/src/main/java/com/cnotes/collect/CollectController.java`
- Create: `server/src/main/java/com/cnotes/common/Hashing.java`
- Test: `server/src/test/java/com/cnotes/collect/CollectApiTest.java`

**Step 1:写请求 DTO**

```java
package com.cnotes.collect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CollectRequest {
    @NotBlank private String url;
    private String title;
    private String author;
    private String content;       // 插件本地 Readability 提取的正文
    private String domSnapshot;   // 兜底(后续抓取计划用)
    private String sourceType;    // 缺省 browser
}
```

**Step 2:写 MD5 工具**

```java
package com.cnotes.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hashing {
    private Hashing() {}
    public static String md5Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
```

**Step 3:写 Service**(url_hash 命中则返回既有 id)

```java
package com.cnotes.collect;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.collect.dto.CollectRequest;
import com.cnotes.common.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectService {

    private final ArticleMapper articleMapper;

    @Transactional
    public String collect(CollectRequest req) {
        String urlHash = Hashing.md5Hex(req.getUrl());
        Article existing = articleMapper.selectOne(
            Wrappers.<Article>lambdaQuery().eq(Article::getUrlHash, urlHash));
        if (existing != null) return existing.getId();

        Article a = new Article();
        a.setUrl(req.getUrl());
        a.setUrlHash(urlHash);
        a.setTitle(req.getTitle());
        a.setAuthor(req.getAuthor());
        a.setContent(req.getContent());
        a.setSourceType(req.getSourceType() == null ? "browser" : req.getSourceType());
        a.setExtractMethod(req.getContent() != null ? "readability" : null);
        a.setStatus("pending");
        a.setRetryCount(0);
        articleMapper.insert(a);
        return a.getId();
    }
}
```

**Step 4:写 Controller**

```java
package com.cnotes.collect;

import com.cnotes.collect.dto.CollectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/collect")
@RequiredArgsConstructor
public class CollectController {

    private final CollectService collectService;

    @PostMapping
    public Map<String, String> collect(@Valid @RequestBody CollectRequest req) {
        return Map.of("id", collectService.collect(req));
    }
}
```

**Step 5:写 API 测试(先失败)**

```java
package com.cnotes.collect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CollectApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String body(String url) throws Exception {
        return om.writeValueAsString(java.util.Map.of("url", url, "title", "t", "content", "hello"));
    }

    @Test
    void collectCreatesPendingArticle() throws Exception {
        mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id", hasLength(32)));
    }

    @Test
    void collectIsIdempotentByUrl() throws Exception {
        String r1 = mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/b")))
                       .andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/collect").contentType("application/json").content(body("https://e.com/b")))
                       .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(r1).isEqualTo(r2);
    }
}
```

**Step 6:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.collect.CollectApiTest"`
Expected: PASS。

**Step 7:提交**

```bash
git add server/src/main/java/com/cnotes/collect server/src/main/java/com/cnotes/common
git add server/src/test/java/com/cnotes/collect/CollectApiTest.java
git commit -m "feat: /api/collect 先入库后处理 + url_hash 幂等"
```

---

## Task 4:模型层(Spring AI `ChatClient` 结构化输出,不自建抽象)

**Files:**
- Create: `server/src/main/java/com/cnotes/organize/OrganizeResult.java`
- Create: `server/src/main/java/com/cnotes/organize/ArticleOrganizer.java`
- Test: `server/src/test/java/com/cnotes/organize/ArticleOrganizerTest.java`

**Step 1:写结果记录**(一次调用拿全)

```java
package com.cnotes.organize;

import java.util.List;

public record OrganizeResult(String summary, List<String> keyPoints, List<String> tags) {}
```

**Step 2:写 Organizer**(直接用 Spring AI `ChatClient`,`.entity()` 出结构化结果——这就是 Spring AI 自带的抽象,不再造 `LlmClient`)

```java
package com.cnotes.organize;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ArticleOrganizer {

    private final ChatClient chatClient;

    public ArticleOrganizer(ChatClient.Builder builder) {  // Spring AI 自动配置注入
        this.chatClient = builder.build();
    }

    public OrganizeResult organize(String title, String content, List<String> allowedTags) {
        String allowed = allowedTags.isEmpty() ? "(暂无)" : String.join("、", allowedTags);
        return chatClient.prompt()
            .system("你是知识管理助手。阅读文章后输出:摘要、3-5 条要点、若干标签。" +
                    "标签优先从受控集中选,确有必要才创造新标签。")
            .user(u -> u.text("受控标签集:{allowed}\n标题:{title}\n正文:\n{content}")
                        .param("allowed", allowed)
                        .param("title", title == null ? "" : title)
                        .param("content", content == null ? "" : content))
            .call()
            .entity(OrganizeResult.class);   // 结构化输出:Spring AI 注入格式指令并反序列化为 record
    }
}
```

**Step 3:写测试(先失败)**——用 stub `ChatModel` 返回固定 JSON,跑通真实 `ChatClient.entity()` 解析路径,不发网络

```java
package com.cnotes.organize;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(ArticleOrganizerTest.StubModelConfig.class)
class ArticleOrganizerTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            // 返回与 OrganizeResult 字段一致的 JSON;.entity() 负责解析
            return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"summary\":\"摘要X\",\"keyPoints\":[\"要点1\",\"要点2\"],\"tags\":[\"Rust\",\"新标签\"]}"))));
        }
    }

    @Autowired ArticleOrganizer organizer;

    @Test
    void parsesStructuredOutput() {
        OrganizeResult r = organizer.organize("标题", "正文", List.of("Rust"));
        assertThat(r.summary()).isEqualTo("摘要X");
        assertThat(r.keyPoints()).containsExactly("要点1", "要点2");
        assertThat(r.tags()).contains("Rust", "新标签");
    }
}
```

> 注:`ChatModel.call(Prompt)` 为主抽象方法,可用 lambda;若因接口含多抽象方法无法 lambda,改写成具名内部类覆写 `call(Prompt)`。`@Primary` 让 `ChatClient.Builder` 采用 stub 而非 OpenAI 自动配置的模型;测试 `application.yml` 已置 dummy key 防启动报错。

**Step 4:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.organize.ArticleOrganizerTest"`
Expected: PASS。

**Step 5:提交**

```bash
git add server/src/main/java/com/cnotes/organize server/src/test/java/com/cnotes/organize
git commit -m "feat: 模型层用 spring ai chatclient 结构化输出,删除自建抽象"
```

---

## Task 5:受控标签归类(方案 B:命中入 article_tag,新标签入 tag_suggestion)

**Files:**
- Create: `server/src/main/java/com/cnotes/tag/entity/Tag.java`
- Create: `server/src/main/java/com/cnotes/tag/entity/ArticleTag.java`
- Create: `server/src/main/java/com/cnotes/tag/entity/TagSuggestion.java`
- Create: `server/src/main/java/com/cnotes/tag/mapper/{TagMapper,ArticleTagMapper,TagSuggestionMapper}.java`
- Create: `server/src/main/java/com/cnotes/tag/TagClassifier.java`
- Test: `server/src/test/java/com/cnotes/tag/TagClassifierTest.java`

**Step 1:写三个实体**——均含 `create_time`(`FieldFill.INSERT`)/`update_time`(`FieldFill.INSERT_UPDATE`)字段,与 `Article` 一致。`Tag{id,name,description}`、`ArticleTag{id,articleId,tagId,confidence}`、`TagSuggestion{id,articleId,name,confidence,status}`。三个 Mapper 各 `extends BaseMapper<...>`。

**Step 2:写归类器**

```java
package com.cnotes.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.tag.entity.*;
import com.cnotes.tag.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagClassifier {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagSuggestionMapper suggestionMapper;

    public List<String> allowedTagNames() {
        return tagMapper.selectList(null).stream().map(Tag::getName).toList();
    }

    @Transactional
    public void apply(String articleId, List<String> modelTags) {
        for (String name : modelTags) {
            Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
            if (tag != null) {
                if (articleTagMapper.selectCount(Wrappers.<ArticleTag>lambdaQuery()
                        .eq(ArticleTag::getArticleId, articleId)
                        .eq(ArticleTag::getTagId, tag.getId())) == 0) {
                    ArticleTag at = new ArticleTag();
                    at.setArticleId(articleId); at.setTagId(tag.getId());
                    articleTagMapper.insert(at);
                }
            } else {
                if (suggestionMapper.selectCount(Wrappers.<TagSuggestion>lambdaQuery()
                        .eq(TagSuggestion::getArticleId, articleId)
                        .eq(TagSuggestion::getName, name)) == 0) {
                    TagSuggestion s = new TagSuggestion();
                    s.setArticleId(articleId); s.setName(name); s.setStatus("pending");
                    suggestionMapper.insert(s);
                }
            }
        }
    }
}
```

**Step 3:写测试(先失败)**

```java
package com.cnotes.tag;

import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TagClassifierTest {

    @Autowired TagClassifier classifier;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired TagSuggestionMapper suggestionMapper;

    @Test
    void hitGoesToArticleTagMissGoesToSuggestion() {
        Tag t = new Tag(); t.setName("Rust"); tagMapper.insert(t);
        String articleId = "a1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, List.of("Rust", "某新概念"));
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }

    @Test
    void applyIsIdempotent() {
        Tag t = new Tag(); t.setName("LLM"); tagMapper.insert(t);
        String articleId = "a2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, List.of("LLM", "新X"));
        classifier.apply(articleId, List.of("LLM", "新X"));
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }
}
```

**Step 4:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.tag.TagClassifierTest"`
Expected: PASS。

**Step 5:提交**

```bash
git add server/src/main/java/com/cnotes/tag server/src/test/java/com/cnotes/tag
git commit -m "feat: 受控标签归类,命中入 article_tag 新标签入 tag_suggestion"
```

---

## Task 6:异步 Worker(状态机 happy path:pending→processing→done)

**Files:**
- Create: `server/src/main/java/com/cnotes/worker/ArticleProcessor.java`
- Create: `server/src/main/java/com/cnotes/worker/ArticleWorker.java`
- Test: `server/src/test/java/com/cnotes/worker/ArticleProcessorTest.java`

**Step 1:写处理器**(调 `ArticleOrganizer` 一次拿全 → 落库 → done)

```java
package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import com.cnotes.tag.TagClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArticleProcessor {

    private final ArticleMapper articleMapper;
    private final ArticleOrganizer organizer;
    private final TagClassifier tagClassifier;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(Article a) {
        try {
            OrganizeResult r = organizer.organize(a.getTitle(), a.getContent(), tagClassifier.allowedTagNames());
            a.setSummary(r.summary());
            a.setKeyPoints(objectMapper.writeValueAsString(r.keyPoints()));
            tagClassifier.apply(a.getId(), r.tags());
            a.setStatus("done");
            a.setProcessedAt(LocalDateTime.now());
            a.setLastError(null);
            articleMapper.updateById(a);
        } catch (Exception e) {
            throw new RuntimeException("organize failed", e);  // 退避在 Worker 层(Task 7)
        }
    }
}
```

**Step 2:写 Worker 轮询骨架**(乐观认领防并发重复)

```java
package com.cnotes.worker;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ArticleWorker {

    private final ArticleMapper articleMapper;
    private final ArticleProcessor processor;

    @Value("${worker.max-retry:5}") int maxRetry;
    @Value("${worker.backoff-base-seconds:30}") int backoffBase;

    @Scheduled(fixedDelayString = "${worker.poll-interval-ms:5000}")
    public void poll() {
        List<Article> batch = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
            .and(w -> w.eq(Article::getStatus, "pending")
                       .or(q -> q.eq(Article::getStatus, "failed")
                                 .le(Article::getNextRetryTime, LocalDateTime.now())))
            .last("LIMIT 10"));
        for (Article a : batch) {
            if (!claim(a)) continue;
            runOne(a);
        }
    }

    private boolean claim(Article a) {
        Article upd = new Article();
        upd.setId(a.getId());
        upd.setStatus("processing");
        return articleMapper.update(upd, Wrappers.<Article>lambdaUpdate()
            .eq(Article::getId, a.getId())
            .in(Article::getStatus, "pending", "failed")) == 1;
    }

    void runOne(Article a) {
        a.setStatus("processing");
        processor.process(a);   // Task 7 在此外层加 try/catch 退避
    }
}
```

**Step 3:写处理器测试(先失败)**——`@MockitoBean ArticleOrganizer` 给定返回(Boot 4 用 `@MockitoBean`,非旧 `@MockBean`)

```java
package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ArticleProcessorTest {

    @Autowired ArticleProcessor processor;
    @Autowired ArticleMapper articleMapper;
    @MockitoBean ArticleOrganizer organizer;

    @Test
    void processFillsSummaryPointsAndMarksDone() {
        when(organizer.organize(any(), any(), any()))
            .thenReturn(new OrganizeResult("摘要", List.of("要点1", "要点2"), List.of("Rust", "新标签")));

        Article a = new Article();
        a.setUrl("https://e.com/p"); a.setUrlHash("00000000000000000000000000000099");
        a.setTitle("标题"); a.setContent("正文"); a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        processor.process(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("done");
        assertThat(got.getSummary()).isEqualTo("摘要");
        assertThat(got.getKeyPoints()).contains("要点1");
        assertThat(got.getProcessedAt()).isNotNull();
    }
}
```

**Step 4:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.worker.ArticleProcessorTest"`
Expected: PASS。

**Step 5:提交**

```bash
git add server/src/main/java/com/cnotes/worker server/src/test/java/com/cnotes/worker/ArticleProcessorTest.java
git commit -m "feat: 异步 worker 处理器 + 轮询认领,happy path 到 done"
```

---

## Task 7:失败重试 + 指数退避(processing→failed→到期重试)

**Files:**
- Modify: `server/src/main/java/com/cnotes/worker/ArticleWorker.java`(`runOne` 加 try/catch 退避)
- Test: `server/src/test/java/com/cnotes/worker/WorkerRetryTest.java`

**Step 1:改 `runOne` 加退避**

```java
void runOne(Article a) {
    try {
        a.setStatus("processing");
        processor.process(a);
    } catch (Exception e) {
        int next = (a.getRetryCount() == null ? 0 : a.getRetryCount()) + 1;
        String msg = String.valueOf(e.getMessage());
        Article upd = new Article();
        upd.setId(a.getId());
        upd.setRetryCount(next);
        upd.setStatus("failed");
        upd.setLastError(msg.substring(0, Math.min(1000, msg.length())));
        if (next < maxRetry) {
            long delay = (long) (backoffBase * Math.pow(2, next - 1)); // 指数退避
            upd.setNextRetryTime(java.time.LocalDateTime.now().plusSeconds(delay));
        } else {
            upd.setNextRetryTime(null);   // 达上限,不再重试
        }
        articleMapper.updateById(upd);
    }
}
```

**Step 2:写重试测试(先失败)**——`@MockitoBean ArticleOrganizer` 抛异常

```java
package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.organize.ArticleOrganizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class WorkerRetryTest {

    @Autowired ArticleWorker worker;
    @Autowired ArticleMapper articleMapper;
    @MockitoBean ArticleOrganizer organizer;

    @Test
    void failureSetsFailedAndSchedulesBackoff() {
        when(organizer.organize(any(), any(), any())).thenThrow(new RuntimeException("model down"));

        Article a = new Article();
        a.setUrl("https://e.com/f"); a.setUrlHash("000000000000000000000000000000f1");
        a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        worker.runOne(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("failed");
        assertThat(got.getRetryCount()).isEqualTo(1);
        assertThat(got.getNextRetryTime()).isNotNull();
        assertThat(got.getLastError()).contains("model down");
    }
}
```

**Step 3:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.worker.WorkerRetryTest"`
Expected: PASS。

**Step 4:提交**

```bash
git add server/src/main/java/com/cnotes/worker/ArticleWorker.java server/src/test/java/com/cnotes/worker/WorkerRetryTest.java
git commit -m "feat: worker 失败重试 + 指数退避"
```

---

## Task 8:读取接口(收件箱列表 + 文章详情)

**Files:**
- Create: `server/src/main/java/com/cnotes/article/dto/{ArticleCardDto,ArticleDetailDto}.java`
- Create: `server/src/main/java/com/cnotes/article/ArticleQueryService.java`
- Create: `server/src/main/java/com/cnotes/article/ArticleController.java`
- Test: `server/src/test/java/com/cnotes/article/ArticleApiTest.java`

**Step 1:写两个 DTO**——`ArticleCardDto{id,title,author,sourceType,summary,status,createTime}`(收件箱卡片,不含正文);`ArticleDetailDto` 在其上加 `content,keyPoints(List<String>)`。

**Step 2:写查询 Service**

```java
package com.cnotes.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.*;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private final ArticleMapper articleMapper;
    private final ObjectMapper om;

    public List<ArticleCardDto> listInbox() {
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .orderByDesc(Article::getCreateTime))
            .stream().map(this::toCard).toList();
    }

    public ArticleDetailDto detail(String id) {
        Article a = articleMapper.selectById(id);
        if (a == null) return null;
        ArticleDetailDto d = new ArticleDetailDto();
        d.setId(a.getId()); d.setTitle(a.getTitle()); d.setAuthor(a.getAuthor());
        d.setSummary(a.getSummary()); d.setContent(a.getContent()); d.setStatus(a.getStatus());
        d.setKeyPoints(parsePoints(a.getKeyPoints()));
        return d;
    }

    private List<String> parsePoints(String json) {
        try { return json == null ? List.of() : om.readValue(json, new TypeReference<List<String>>(){}); }
        catch (Exception e) { return List.of(); }
    }

    private ArticleCardDto toCard(Article a) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(a.getId()); c.setTitle(a.getTitle()); c.setAuthor(a.getAuthor());
        c.setSourceType(a.getSourceType()); c.setSummary(a.getSummary());
        c.setStatus(a.getStatus()); c.setCreateTime(a.getCreateTime());
        return c;
    }
}
```

**Step 3:写 Controller**——`GET /api/articles`(列表)、`GET /api/articles/{id}`(详情,null→404)。

```java
package com.cnotes.article;

import com.cnotes.article.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleQueryService queryService;

    @GetMapping
    public List<ArticleCardDto> inbox() { return queryService.listInbox(); }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDetailDto> detail(@PathVariable String id) {
        ArticleDetailDto d = queryService.detail(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }
}
```

**Step 4:写 API 测试(先失败)**

```java
package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ArticleApiTest {

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;

    private String seed() {
        Article a = new Article();
        a.setUrl("https://e.com/list"); a.setUrlHash("000000000000000000000000000000a1");
        a.setTitle("收件箱标题"); a.setSummary("摘要"); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    void inboxListsArticles() throws Exception {
        seed();
        mvc.perform(get("/api/articles"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].title", hasItem("收件箱标题")));
    }

    @Test
    void detailReturnsArticle() throws Exception {
        String id = seed();
        mvc.perform(get("/api/articles/" + id))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title", is("收件箱标题")));
    }
}
```

**Step 5:跑测试验证通过**

Run: `cd server && ./gradlew test --tests "com.cnotes.article.ArticleApiTest"`
Expected: PASS。

**Step 6:全量回归**

Run: `cd server && ./gradlew test`
Expected: 全部 PASS。

**Step 7:提交**

```bash
git add server/src/main/java/com/cnotes/article server/src/test/java/com/cnotes/article/ArticleApiTest.java
git commit -m "feat: 收件箱列表与文章详情读取接口"
```

---

## 完成后的状态与下一步

跑通后,后端脊柱即可端到端演示:`POST /api/collect` 入库 →(几秒内)Worker 经 Spring AI 出摘要/要点/标签 → `GET /api/articles` 看收件箱 → `GET /api/articles/{id}` 看详情。本地全程 H2,上线把数据源切到 MySQL(Flyway 自动走 `db/migration/mysql`)。

**本计划刻意不含(YAGNI,留给后续计划):**
- 真实模型 Provider 选型与 api-key、提示词调优、token/限流:Spring AI starter 已就位,选定后只配置不改代码。
- 二/三级抓取(模型清洗 DOM 快照、服务器无头浏览器):`content` 缺失或质量差时的兜底,独立计划。
- `note` 读写 API、`/api/articles/{id}/notes`:并入**阅读端实现计划**(配合 web-reader 真实前端)。
- 微信收集(`/wechat/callback`):V2。
- 鉴权/JWT、对象存储:多机预留原则已在结构上留口,落地待产品化阶段。

**已知风险/执行前确认:**
- Spring AI 2.0 GA(2026-06-12)+ Spring Boot 4 + Java 21 为很新的组合;首次 `./gradlew build` 若遇版本解析问题,对齐 Spring AI 2.0 BOM 与 `mybatis-plus-spring-boot4-starter` 最新版,或退到 Spring AI 1.0.9 + Boot 3.3 稳定组合。
- 构建须能访问 Maven Central(当前 Claude 沙箱不放行,需在本机或放开白名单的环境执行)。

**建议下一份计划:** 浏览器插件(收集入口)或阅读端真实前端,二选一打通「收 → 读」可视闭环。
