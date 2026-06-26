package com.cnotes.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件对象存储(A2):对象落在 ${storage.root-dir}/<key>,key 为 uuid。
 * 目录按需自动创建;key 校验杜绝路径穿越(只允许文件名,不含分隔符/..)。
 */
@Service
public class LocalFileStorageService implements StorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${storage.root-dir:./data/content}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, String content) {
        try {
            Files.createDirectories(root);
            Files.writeString(resolve(key), content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("storage put failed: " + key, e);
        }
    }

    @Override
    public String get(String key) {
        Path p = resolve(key);
        if (!Files.exists(p)) return null;
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("storage get failed: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("storage delete failed: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    /** key 必须是纯文件名(uuid),拒绝任何路径分隔符/.. 以防穿越到 root 之外。 */
    private Path resolve(String key) {
        if (key == null || key.isEmpty()
                || key.indexOf('/') >= 0 || key.indexOf('\\') >= 0
                || key.indexOf("..") >= 0) {
            throw new IllegalArgumentException("invalid storage key: " + key);
        }
        return root.resolve(key);
    }
}
