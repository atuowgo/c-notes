# MVP 后端骨架 实现计划 (Backend Spine)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 立起「个人知识炼金炉」的后端脊柱——收文入库(先入库后处理)、异步 Worker 出摘要/要点/标签(一次模型调用)、受控标签归类、收件箱读取接口——可在本地连真实 MySQL 跑通。

**Architecture:** 单模块 Spring Boot 常驻服务,对外暴露 HTTP。`/api/collect` 收提交即写 `article(status=pending)` 立即返回;同进程 `@Scheduled` Worker 轮询 `pending`/到期 `failed` → 调模型抽象层一次拿全 summary+key_points+tags(JSON)→ 受控标签入 `article_tag`、新标签入 `tag_suggestion` → 置 `done`;失败按指数退避。收/发逻辑分模块拆开(为将来多机预留)。模型与任务投递各抽一层,便于替换。

**Tech Stack:** Java 17、Spring Boot 3.2.x、MyBatis-Plus(mybatis-plus-spring-boot3-starter)、Flyway(flyway-core + flyway-mysql)、MySQL 8、Lombok、JUnit 5 + Testcontainers(mysql)。构建 Maven(`./mvnw`)。

**约定(贯穿全程):**
- 建表规范:`id CHAR(32)` 物理主键(MyBatis-Plus `IdType.ASSIGN_UUID` 生成 32 位无横线 UUID);长字段唯一键用 `xxx_hash CHAR(32)`;`create_time`/`update_time` 由 **MySQL DDL 的 `DEFAULT/ON UPDATE CURRENT_TIMESTAMP` 托管**,应用侧不写这两列(实体字段 insert/update 策略设 `NEVER`)。
- 所有 DDL 走 Flyway,**绝不**用 MyBatis-Plus 自动建表;测试用 Testcontainers 起真实 MySQL 8 跑 Flyway,确保 `ON UPDATE CURRENT_TIMESTAMP` 等 MySQL 语义被真实验证。
- 包根 `com.cnotes`;收(`web`/`collect`)与发(`worker`)分包。
- DRY / YAGNI / TDD / 每个 Task 末尾提交一次。
- 先决条件:本机已装 Docker(Testcontainers 需要)、JDK 17。

---

## Task 0:项目骨架与 Testcontainers 基座

**Files:**
- Create: `server/pom.xml`
- Create: `server/src/main/java/com/cnotes/CNotesApplication.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/test/java/com/cnotes/AbstractMySqlTest.java`
- Create: `server/src/test/java/com/cnotes/CNotesApplicationTests.java`
- Create: `server/.mvn/wrapper/maven-wrapper.properties`(`mvn -N wrapper:wrapper` 生成)

**Step 1:写 `pom.xml`**

关键依赖:`spring-boot-starter-web`、`spring-boot-starter-validation`、`mybatis-plus-spring-boot3-starter:3.5.7`、`flyway-core` + `flyway-mysql`、`mysql-connector-j`(runtime)、`lombok`(provided);test:`spring-boot-starter-test`、`org.testcontainers:junit-jupiter`、`org.testcontainers:mysql`。`<java.version>17</java.version>`,parent `spring-boot-starter-parent:3.2.5`。

**Step 2:写主类**

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

**Step 3:写 `application.yml`**(Flyway 开启;数据源占位,测试由 Testcontainers 覆盖)

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  datasource:
    url: jdbc:mysql://localhost:3306/cnotes?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
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

**Step 4:写 Testcontainers 基座**

```java
package com.cnotes;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("cnotes");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
}
```

**Step 5:写冒烟测试**

```java
package com.cnotes;

import org.junit.jupiter.api.Test;

class CNotesApplicationTests extends AbstractMySqlTest {
    @Test
    void contextLoads() { }
}
```

**Step 6:跑测试验证基座**

Run: `cd server && ./mvnw -q test -Dtest=CNotesApplicationTests`
Expected: 首次会拉 mysql:8.0 镜像;PASS(Spring 上下文 + Testcontainers MySQL 起得来)。
注:此时 `db/migration` 为空,Flyway 无脚本即空跑,不报错。

**Step 7:提交**

```bash
git add server/pom.xml server/src server/.mvn
git commit -m "chore: spring boot 骨架 + testcontainers mysql 基座"
```

---

## Task 1:Flyway 建 5 张表

**Files:**
- Create: `server/src/main/resources/db/migration/V1__init_schema.sql`
- Test: `server/src/test/java/com/cnotes/schema/SchemaMigrationTest.java`

**Step 1:写建表迁移脚本**(严格遵守建表规范)

```sql
-- article:文章主表
CREATE TABLE article (
    id              CHAR(32)      NOT NULL COMMENT '32位UUID hex',
    url             VARCHAR(2048) NOT NULL COMMENT '原文链接',
    url_hash        CHAR(32)      NOT NULL COMMENT 'MD5(url) hex,唯一索引用',
    title           VARCHAR(512)           DEFAULT NULL,
    author          VARCHAR(256)           DEFAULT NULL,
    source_type     VARCHAR(32)   NOT NULL DEFAULT 'browser' COMMENT 'browser/wechat',
    content         LONGTEXT               DEFAULT NULL COMMENT '正文Markdown',
    summary         TEXT                   DEFAULT NULL COMMENT '自动摘要',
    key_points      JSON                   DEFAULT NULL COMMENT '要点数组',
    status          VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/done/failed',
    extract_method  VARCHAR(32)            DEFAULT NULL COMMENT 'readability/model_clean/headless',
    retry_count     INT           NOT NULL DEFAULT 0,
    next_retry_time DATETIME               DEFAULT NULL COMMENT '下次重试(退避)',
    last_error      VARCHAR(1024)          DEFAULT NULL,
    processed_at    DATETIME               DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_hash (url_hash),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章';

-- tag:受控标签集
CREATE TABLE tag (
    id          CHAR(32)    NOT NULL,
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(256)         DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签';

-- article_tag:文章-标签关联
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

-- tag_suggestion:AI 选不准的新标签,待确认转正
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

-- note:划线/想法(本计划只建表,API 在阅读端计划)
CREATE TABLE note (
    id          CHAR(32) NOT NULL,
    article_id  CHAR(32) NOT NULL,
    quote       TEXT     NOT NULL COMMENT '划线引文',
    thought     TEXT              DEFAULT NULL COMMENT '想法,可空',
    anchor      JSON              DEFAULT NULL COMMENT '正文定位 selector+offset',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_id (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='划线/想法';
```

**Step 2:写迁移验证测试(先失败)**

```java
package com.cnotes.schema;

import com.cnotes.AbstractMySqlTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationTest extends AbstractMySqlTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void allFiveTablesExist() {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = DATABASE() AND table_name IN " +
            "('article','tag','article_tag','tag_suggestion','note')", Integer.class);
        assertThat(n).isEqualTo(5);
    }

    @Test
    void updateTimeAutoUpdatesOnRowChange() throws Exception {
        jdbc.update("INSERT INTO tag (id, name) VALUES ('t-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'x')");
        var t1 = jdbc.queryForObject("SELECT update_time FROM tag WHERE name='x'", java.sql.Timestamp.class);
        Thread.sleep(1100);
        jdbc.update("UPDATE tag SET description='y' WHERE name='x'");
        var t2 = jdbc.queryForObject("SELECT update_time FROM tag WHERE name='x'", java.sql.Timestamp.class);
        assertThat(t2).isAfter(t1); // 验证 ON UPDATE CURRENT_TIMESTAMP 真实生效
    }
}
```

**Step 3:跑测试验证失败**

Run: `cd server && ./mvnw -q test -Dtest=SchemaMigrationTest`
Expected: 若先于 Step 1 跑则 FAIL;脚本就位后此步用于确认通过路径。

**Step 4:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=SchemaMigrationTest`
Expected: PASS(5 张表存在 + `update_time` 自动刷新)。

**Step 5:提交**

```bash
git add server/src/main/resources/db/migration server/src/test/java/com/cnotes/schema
git commit -m "feat: flyway 建 article/tag/article_tag/tag_suggestion/note 五表"
```

---

## Task 2:Article 实体 + Mapper(时间戳交给 DB)

**Files:**
- Create: `server/src/main/java/com/cnotes/article/entity/Article.java`
- Create: `server/src/main/java/com/cnotes/article/mapper/ArticleMapper.java`
- Test: `server/src/test/java/com/cnotes/article/ArticleMapperTest.java`

**Step 1:写实体**(`create_time`/`update_time` 策略 `NEVER`,完全交给 DB)

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
    private String keyPoints;     // 原始 JSON 字符串,Service 层序列化
    private String status;
    private String extractMethod;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime processedAt;
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
```

**Step 2:写 Mapper**

```java
package com.cnotes.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cnotes.article.entity.Article;

public interface ArticleMapper extends BaseMapper<Article> {
}
```

**Step 3:写往返测试(先失败)**

```java
package com.cnotes.article;

import com.cnotes.AbstractMySqlTest;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleMapperTest extends AbstractMySqlTest {

    @Autowired ArticleMapper mapper;

    @Test
    void insertAssignsUuidAndDbTimestamps() {
        Article a = new Article();
        a.setUrl("https://e.com/x");
        a.setUrlHash("00000000000000000000000000000001");
        a.setStatus("pending");
        mapper.insert(a);

        assertThat(a.getId()).hasSize(32);          // ASSIGN_UUID
        Article got = mapper.selectById(a.getId());
        assertThat(got.getCreateTime()).isNotNull(); // DB DEFAULT 填充
        assertThat(got.getUpdateTime()).isNotNull();
    }
}
```

**Step 4:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=ArticleMapperTest`
Expected: PASS(id 32 位、时间戳由 DB 填)。

**Step 5:提交**

```bash
git add server/src/main/java/com/cnotes/article
git add server/src/test/java/com/cnotes/article/ArticleMapperTest.java
git commit -m "feat: article 实体与 mapper,时间戳交由 mysql 托管"
```

---

## Task 3:`/api/collect` 入库(先入库后处理 + url_hash 幂等)

**Files:**
- Create: `server/src/main/java/com/cnotes/collect/dto/CollectRequest.java`
- Create: `server/src/main/java/com/cnotes/collect/CollectService.java`
- Create: `server/src/main/java/com/cnotes/collect/CollectController.java`
- Create: `server/src/main/java/com/cnotes/common/Hashing.java`
- Test: `server/src/test/java/com/cnotes/collect/CollectApiTest.java`

**Step 1:写请求 DTO**(对应插件载荷 `url/title/content/dom_snapshot/source_type`)

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
    private String domSnapshot;   // 兜底(本 Task 暂存 content 为空时用,后续抓取计划再用)
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
            return HexFormat.of().formatHex(d); // 32 位
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
```

**Step 3:写 Service**(幂等:url_hash 命中则返回既有 id,不重复入库)

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
        a.setStatus("pending");      // 先入库,Worker 后处理
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
        return Map.of("id", collectService.collect(req)); // 立即返回,不阻塞
    }
}
```

**Step 5:写 API 测试(先失败)**

```java
package com.cnotes.collect;

import com.cnotes.AbstractMySqlTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class CollectApiTest extends AbstractMySqlTest {

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
        org.assertj.core.api.Assertions.assertThat(r1).isEqualTo(r2); // 同 url 返回同 id
    }
}
```

**Step 6:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=CollectApiTest`
Expected: PASS(入库返回 32 位 id;同 url 幂等)。

**Step 7:提交**

```bash
git add server/src/main/java/com/cnotes/collect server/src/main/java/com/cnotes/common
git add server/src/test/java/com/cnotes/collect/CollectApiTest.java
git commit -m "feat: /api/collect 先入库后处理 + url_hash 幂等"
```

---

## Task 4:模型抽象层 + 测试假实现

**Files:**
- Create: `server/src/main/java/com/cnotes/llm/OrganizeResult.java`
- Create: `server/src/main/java/com/cnotes/llm/LlmClient.java`
- Create: `server/src/main/java/com/cnotes/llm/StubLlmClient.java`
- Test: `server/src/test/java/com/cnotes/llm/StubLlmClientTest.java`

**Step 1:写结果记录**(一次调用拿全:摘要 + 要点 + 标签)

```java
package com.cnotes.llm;

import java.util.List;

public record OrganizeResult(String summary, List<String> keyPoints, List<String> tags) {}
```

**Step 2:写抽象接口**(厂商/型号收敛在实现里;入参带受控标签集)

```java
package com.cnotes.llm;

import java.util.List;

public interface LlmClient {
    /** 合并一次调用:给定标题/正文 + 允许的标签集,返回摘要/要点/标签(JSON 已解析)。 */
    OrganizeResult organize(String title, String content, List<String> allowedTags);
}
```

**Step 3:写默认假实现**(MVP 无真实 key 时可跑;真实实现后续 Task/计划替换)

```java
package com.cnotes.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class StubLlmClient {
    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient stubLlmClient() {
        return (title, content, allowedTags) -> {
            String summary = (title == null ? "" : title) + " 摘要(stub)";
            List<String> kps = List.of("要点1", "要点2");
            // 命中第一个允许标签 + 一个新标签(触发 tag_suggestion 分支)
            List<String> tags = allowedTags.isEmpty()
                    ? List.of("新标签")
                    : List.of(allowedTags.get(0), "新标签");
            return new OrganizeResult(summary, kps, tags);
        };
    }
}
```

**Step 4:写测试(先失败)**

```java
package com.cnotes.llm;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class StubLlmClientTest {
    @Test
    void stubReturnsSummaryPointsAndTags() {
        LlmClient c = new StubLlmClient().stubLlmClient();
        OrganizeResult r = c.organize("标题", "正文", List.of("LLM 推理优化"));
        assertThat(r.summary()).contains("摘要");
        assertThat(r.keyPoints()).hasSize(2);
        assertThat(r.tags()).contains("LLM 推理优化", "新标签");
    }
}
```

**Step 5:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=StubLlmClientTest`
Expected: PASS。

**Step 6:提交**

```bash
git add server/src/main/java/com/cnotes/llm server/src/test/java/com/cnotes/llm
git commit -m "feat: 模型抽象层 LlmClient + 测试用 stub 实现"
```

---

## Task 5:受控标签归类(方案 B:命中入 article_tag,新标签入 tag_suggestion)

**Files:**
- Create: `server/src/main/java/com/cnotes/tag/entity/Tag.java`
- Create: `server/src/main/java/com/cnotes/tag/entity/ArticleTag.java`
- Create: `server/src/main/java/com/cnotes/tag/entity/TagSuggestion.java`
- Create: `server/src/main/java/com/cnotes/tag/mapper/TagMapper.java`
- Create: `server/src/main/java/com/cnotes/tag/mapper/ArticleTagMapper.java`
- Create: `server/src/main/java/com/cnotes/tag/mapper/TagSuggestionMapper.java`
- Create: `server/src/main/java/com/cnotes/tag/TagClassifier.java`
- Test: `server/src/test/java/com/cnotes/tag/TagClassifierTest.java`

**Step 1:写三个实体**(均含 `create_time`/`update_time` 的 `NEVER` 策略字段,与 Article 一致;此处略写共性,实体字段对齐 Task 1 DDL)。`Tag{id,name,description}`、`ArticleTag{id,articleId,tagId,confidence}`、`TagSuggestion{id,articleId,name,confidence,status}`。三个 Mapper 各 `extends BaseMapper<...>`。

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

    /** 受控标签集 = 当前 tag 表全部 name。 */
    public List<String> allowedTagNames() {
        return tagMapper.selectList(null).stream().map(Tag::getName).toList();
    }

    /** 命中受控集 → article_tag;否则 → tag_suggestion(去重靠各自唯一键)。 */
    @Transactional
    public void apply(String articleId, List<String> modelTags) {
        for (String name : modelTags) {
            Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
            if (tag != null) {
                if (articleTagMapper.selectCount(Wrappers.<ArticleTag>lambdaQuery()
                        .eq(ArticleTag::getArticleId, articleId)
                        .eq(ArticleTag::getTagId, tag.getId())) == 0) {
                    ArticleTag at = new ArticleTag();
                    at.setArticleId(articleId);
                    at.setTagId(tag.getId());
                    articleTagMapper.insert(at);
                }
            } else {
                if (suggestionMapper.selectCount(Wrappers.<TagSuggestion>lambdaQuery()
                        .eq(TagSuggestion::getArticleId, articleId)
                        .eq(TagSuggestion::getName, name)) == 0) {
                    TagSuggestion s = new TagSuggestion();
                    s.setArticleId(articleId);
                    s.setName(name);
                    s.setStatus("pending");
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

import com.cnotes.AbstractMySqlTest;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TagClassifierTest extends AbstractMySqlTest {

    @Autowired TagClassifier classifier;
    @Autowired TagMapper tagMapper;
    @Autowired ArticleTagMapper articleTagMapper;
    @Autowired TagSuggestionMapper suggestionMapper;

    @Test
    void hitGoesToArticleTagMissGoesToSuggestion() {
        Tag t = new Tag(); t.setName("Rust"); tagMapper.insert(t);
        String articleId = "a1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        classifier.apply(articleId, List.of("Rust", "某新概念"));

        assertThat(articleTagMapper.selectList(null)).hasSize(1); // 命中
        assertThat(suggestionMapper.selectList(null)).hasSize(1); // 新标签入待确认
    }

    @Test
    void applyIsIdempotent() {
        Tag t = new Tag(); t.setName("LLM"); tagMapper.insert(t);
        String articleId = "a2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        classifier.apply(articleId, List.of("LLM", "新X"));
        classifier.apply(articleId, List.of("LLM", "新X")); // 二次不应重复
        assertThat(articleTagMapper.selectList(null)).hasSize(1);
        assertThat(suggestionMapper.selectList(null)).hasSize(1);
    }
}
```

**Step 4:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=TagClassifierTest`
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

**Step 1:写处理器**(单篇处理:认领 → 调模型 → 落 summary/key_points/tags → done)

```java
package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.llm.LlmClient;
import com.cnotes.llm.OrganizeResult;
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
    private final LlmClient llmClient;
    private final TagClassifier tagClassifier;
    private final ObjectMapper objectMapper;

    /** 处理单篇;已置为 processing 的文章传入。返回是否成功。 */
    @Transactional
    public void process(Article a) {
        try {
            OrganizeResult r = llmClient.organize(a.getTitle(), a.getContent(), tagClassifier.allowedTagNames());
            a.setSummary(r.summary());
            a.setKeyPoints(objectMapper.writeValueAsString(r.keyPoints()));
            tagClassifier.apply(a.getId(), r.tags());
            a.setStatus("done");
            a.setProcessedAt(LocalDateTime.now());
            a.setLastError(null);
            articleMapper.updateById(a);
        } catch (Exception e) {
            throw new RuntimeException("organize failed", e); // 退避在 Worker 层处理(Task 7)
        }
    }
}
```

**Step 2:写 Worker 轮询骨架**(认领用条件更新防并发重复处理)

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
            if (!claim(a)) continue;          // 认领失败=被别的循环抢走
            runOne(a);
        }
    }

    /** 乐观认领:仅当仍处于可处理状态时置 processing。 */
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
        processor.process(a);                 // Task 7 会在此外层加 try/catch 退避
    }
}
```

**Step 3:写处理器测试(先失败)**

```java
package com.cnotes.worker;

import com.cnotes.AbstractMySqlTest;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleProcessorTest extends AbstractMySqlTest {

    @Autowired ArticleProcessor processor;
    @Autowired ArticleMapper articleMapper;

    @Test
    void processFillsSummaryPointsAndMarksDone() {
        Article a = new Article();
        a.setUrl("https://e.com/p"); a.setUrlHash("00000000000000000000000000000099");
        a.setTitle("标题"); a.setContent("正文"); a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        processor.process(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("done");
        assertThat(got.getSummary()).contains("摘要");
        assertThat(got.getKeyPoints()).contains("要点1");
        assertThat(got.getProcessedAt()).isNotNull();
    }
}
```

**Step 4:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=ArticleProcessorTest`
Expected: PASS(stub 模型驱动,summary/key_points 落库,状态 done)。

**Step 5:提交**

```bash
git add server/src/main/java/com/cnotes/worker server/src/test/java/com/cnotes/worker/ArticleProcessorTest.java
git commit -m "feat: 异步 worker 处理器 + 轮询认领骨架,happy path 到 done"
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
        Article upd = new Article();
        upd.setId(a.getId());
        upd.setRetryCount(next);
        upd.setLastError(String.valueOf(e.getMessage()).substring(0, Math.min(1000, String.valueOf(e.getMessage()).length())));
        if (next >= maxRetry) {
            upd.setStatus("failed");
            upd.setNextRetryTime(null);            // 不再重试
        } else {
            upd.setStatus("failed");
            long delay = (long) (backoffBase * Math.pow(2, next - 1)); // 指数退避
            upd.setNextRetryTime(java.time.LocalDateTime.now().plusSeconds(delay));
        }
        articleMapper.updateById(upd);
    }
}
```

**Step 2:写重试测试(先失败)**——用一个会抛异常的 `LlmClient` 覆盖 stub

```java
package com.cnotes.worker;

import com.cnotes.AbstractMySqlTest;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(WorkerRetryTest.FailingLlmConfig.class)
class WorkerRetryTest extends AbstractMySqlTest {

    @TestConfiguration
    static class FailingLlmConfig {
        @Bean LlmClient llmClient() {
            return (t, c, tags) -> { throw new RuntimeException("model down"); };
        }
    }

    @Autowired ArticleWorker worker;
    @Autowired ArticleMapper articleMapper;

    @Test
    void failureSetsFailedAndSchedulesBackoff() {
        Article a = new Article();
        a.setUrl("https://e.com/f"); a.setUrlHash("000000000000000000000000000000f1");
        a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        worker.runOne(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("failed");
        assertThat(got.getRetryCount()).isEqualTo(1);
        assertThat(got.getNextRetryTime()).isNotNull();   // 安排了退避重试
        assertThat(got.getLastError()).contains("model down");
    }
}
```

**Step 3:跑测试验证通过**

Run: `cd server && ./mvnw -q test -Dtest=WorkerRetryTest`
Expected: PASS(失败置 failed、retry_count=1、安排 next_retry_time)。

**Step 4:提交**

```bash
git add server/src/main/java/com/cnotes/worker/ArticleWorker.java server/src/test/java/com/cnotes/worker/WorkerRetryTest.java
git commit -m "feat: worker 失败重试 + 指数退避"
```

---

## Task 8:读取接口(收件箱列表 + 文章详情)

**Files:**
- Create: `server/src/main/java/com/cnotes/article/dto/ArticleCardDto.java`
- Create: `server/src/main/java/com/cnotes/article/dto/ArticleDetailDto.java`
- Create: `server/src/main/java/com/cnotes/article/ArticleQueryService.java`
- Create: `server/src/main/java/com/cnotes/article/ArticleController.java`
- Test: `server/src/test/java/com/cnotes/article/ArticleApiTest.java`

**Step 1:写两个 DTO**——`ArticleCardDto{id,title,author,sourceType,summary,status,createTime}`(收件箱卡片,不含正文);`ArticleDetailDto` 在其上加 `content,keyPoints(List<String>),tags(List<String>)`。

**Step 2:写查询 Service**——`listInbox()` 按 `create_time desc` 取卡片;`detail(id)` 取单篇 + 关联标签 + 反序列化 key_points。

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

**Step 4:写 API 测试(先失败)**——入库一篇 → 列表含该篇 → 详情返回标题。

```java
package com.cnotes.article;

import com.cnotes.AbstractMySqlTest;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ArticleApiTest extends AbstractMySqlTest {

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

Run: `cd server && ./mvnw -q test -Dtest=ArticleApiTest`
Expected: PASS。

**Step 6:全量回归**

Run: `cd server && ./mvnw -q test`
Expected: 全部 PASS。

**Step 7:提交**

```bash
git add server/src/main/java/com/cnotes/article server/src/test/java/com/cnotes/article/ArticleApiTest.java
git commit -m "feat: 收件箱列表与文章详情读取接口"
```

---

## 完成后的状态与下一步

跑通后,后端脊柱即可端到端演示:`POST /api/collect` 入库 →(几秒内)Worker 出摘要/要点/标签 → `GET /api/articles` 看收件箱 → `GET /api/articles/{id}` 看详情。

**本计划刻意不含(YAGNI,留给后续计划):**
- 真实 `LlmClient`(接某厂商 API + JSON 解析容错):待选型后单独小计划替换 `StubLlmClient`。
- 二/三级抓取(模型清洗 DOM 快照、服务器无头浏览器):`content` 缺失或质量差时的兜底,独立计划。
- `note` 读写 API、`/api/articles/{id}/notes`:并入**阅读端实现计划**(配合 web-reader 真实前端)。
- 微信收集(`/wechat/callback`):V2。
- 鉴权/JWT、对象存储:多机预留原则已在结构上留口,落地待产品化阶段。

**建议下一份计划:** 浏览器插件(收集入口)或阅读端真实前端,二选一打通「收 → 读」可视闭环。
