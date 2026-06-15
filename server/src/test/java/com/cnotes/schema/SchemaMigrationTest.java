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
