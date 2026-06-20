package com.cnotes.social.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 评论(线程式一层:parentId 为 NULL 即顶层)。 */
@Data
public class CommentDto {
    private String id;
    private String articleId;
    private String parentId;
    private String body;
    private String authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    /** 是否为文章作者(评论区标「作者」)。 */
    private boolean byArticleAuthor;
    /** 是否当前查看者本人(决定是否显示删除)。 */
    private boolean mine;
    private LocalDateTime createTime;
}
