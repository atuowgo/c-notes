package com.cnotes.auth.oauth;

/**
 * 三方授权登录 SPI。GitHub/Google 走标准授权码流;微信走网站应用扫码(同为授权码流)。
 * 实现仅在配置了 client-id/secret 时 {@link #configured()} 为真;未配置时登录入口返回明确错误。
 */
public interface OAuthProvider {
    /** 渠道名:github / google / wechat。 */
    String name();

    /** 是否已配置 client-id/secret(未配置则不可用)。 */
    boolean configured();

    /** 构造授权跳转 URL(含 state 防 CSRF;微信为出二维码的扫码页 URL)。 */
    String authorizeUrl(String state);

    /** 用回调 code 换取用户身份。失败抛 IllegalStateException。 */
    ProviderUser exchange(String code);
}
