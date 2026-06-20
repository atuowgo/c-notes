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

    @Test
    void multiUserTablesExistAndSystemUserSeeded() {
        Integer tables = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE LOWER(table_name) IN ('app_user','auth_identity')", Integer.class);
        assertThat(tables).isEqualTo(2);

        Integer sys = jdbc.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE id = '00000000000000000000000000000001'", Integer.class);
        assertThat(sys).isEqualTo(1);
    }

}
