package com.cnotes.storage;

/**
 * 对象存储抽象(A2):长正文落盘到外部存储,content 列只留 key。
 * key 为 uuid(无路径分隔符);实现负责目录创建与读写。
 */
public interface StorageService {

    /** 写入对象;key 由调用方生成(uuid)。 */
    void put(String key, String content);

    /** 读回对象;不存在返回 null。 */
    String get(String key);

    /** 删除对象;不存在视为 no-op。 */
    void delete(String key);

    /** 是否存在。 */
    boolean exists(String key);
}
