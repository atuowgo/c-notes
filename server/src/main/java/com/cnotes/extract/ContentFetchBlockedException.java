package com.cnotes.extract;

/**
 * 目标站点明确拒绝抓取(403/401/404/451 等),重试无意义,不同于超时/网络抖动等瞬时失败。
 * Worker 层据此跳过退避重试,直接终态失败。
 */
public class ContentFetchBlockedException extends RuntimeException {
    public ContentFetchBlockedException(String url, int status) {
        super("内容抓取被目标站点拒绝(HTTP " + status + "),不再重试: " + url);
    }
}
