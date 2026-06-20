package com.cnotes.auth.oauth;

/** 三方授权回传的用户身份(已换取)。 */
public record ProviderUser(String provider, String providerUid, String email,
                           String nickname, String avatarUrl) {}
