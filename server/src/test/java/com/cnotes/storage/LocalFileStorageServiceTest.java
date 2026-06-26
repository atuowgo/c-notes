package com.cnotes.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文件存储单测:用 @TempDir 隔离,验证 put/get/delete/exists、目录自动创建、key 路径穿越拦截。
 */
class LocalFileStorageServiceTest {

    @TempDir
    Path root;

    private LocalFileStorageService svc() {
        return new LocalFileStorageService(root.toString());
    }

    @Test
    void putGetRoundTrip() {
        LocalFileStorageService s = svc();
        s.put("key1", "正文内容");
        assertThat(s.exists("key1")).isTrue();
        assertThat(s.get("key1")).isEqualTo("正文内容");
    }

    @Test
    void getMissingReturnsNull() {
        LocalFileStorageService s = svc();
        assertThat(s.exists("nope")).isFalse();
        assertThat(s.get("nope")).isNull();
    }

    @Test
    void deleteRemovesObject() {
        LocalFileStorageService s = svc();
        s.put("key2", "v");
        s.delete("key2");
        assertThat(s.exists("key2")).isFalse();
        assertThat(s.get("key2")).isNull();
    }

    @Test
    void deleteMissingIsNoOp() {
        svc().delete("never-existed");   // 不抛错
    }

    @Test
    void putCreatesRootIfMissing() throws Exception {
        Path nested = root.resolve("deep/sub/dir");
        LocalFileStorageService s = new LocalFileStorageService(nested.toString());
        s.put("key3", "x");
        assertThat(Files.exists(nested.resolve("key3"))).isTrue();
        assertThat(s.get("key3")).isEqualTo("x");
    }

    @Test
    void pathTraversalKeyRejected() {
        LocalFileStorageService s = svc();
        assertThatThrownBy(() -> s.put("../escape", "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
