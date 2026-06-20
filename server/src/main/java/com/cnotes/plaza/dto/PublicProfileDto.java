package com.cnotes.plaza.dto;

import lombok.Data;

/**
 * 用户公开主页头部:昵称/头像 + 对外可见的统计。
 * 关注/粉丝数属阶段 4(follow),本期为 0。「炼金总数」是私域信息,不外露;只露公开数与被收录数。
 */
@Data
public class PublicProfileDto {
    private String userId;
    private String nickname;
    private String avatarUrl;
    /** 公开文章数。 */
    private long publicCount;
    /** 被收录总次数(其公开文章被他人收录的累计)。 */
    private long collectedTotal;
    /** 被收藏总次数。 */
    private long bookmarkedTotal;
    /** 关注数(阶段 4)。 */
    private long following;
    /** 粉丝数(阶段 4)。 */
    private long followers;
    /** 当前登录者是否已关注此人。 */
    private boolean followedByMe;
}
