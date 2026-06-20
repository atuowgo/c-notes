package com.cnotes.share;

import com.cnotes.article.entity.Article;
import com.cnotes.auth.ShareLevel;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 分享级别解析:文章生效级别 = 逐篇覆盖 ?? 所有者账号默认。 */
@Service
@RequiredArgsConstructor
public class ShareService {

    private final UserMapper userMapper;

    public ShareLevel effectiveLevel(Article a) {
        if (a.getShareLevel() != null) return ShareLevel.parse(a.getShareLevel());
        User owner = userMapper.selectById(a.getOwnerId());
        return owner == null ? ShareLevel.PRIVATE : ShareLevel.parse(owner.getDefaultShareLevel());
    }

    /** 是否对外可见(匿名只读门槛):生效级别 >= READ_ONLY。 */
    public boolean publiclyVisible(Article a) {
        return effectiveLevel(a).atLeast(ShareLevel.READ_ONLY);
    }
}
