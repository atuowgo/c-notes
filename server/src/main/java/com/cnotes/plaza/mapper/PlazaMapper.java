package com.cnotes.plaza.mapper;

import com.cnotes.article.entity.Article;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 广场候选池查询:筛出「对外公开」(生效分享级别 >= READ_ONLY)且已就绪的文章。
 * 生效级别 = 逐篇 share_level ?? 所有者 default_share_level —— 二者都用「公开枚举集合」的 IN 判定,
 * 标准 SQL,h2(MySQL 模式)与 MySQL 通用。按最新截取 cap 条后,在内存计算质量分并排序/分页。
 */
public interface PlazaMapper {

    String PUBLIC_LEVELS = "('READ_ONLY','BOOKMARKABLE','COLLECTABLE','ANNOTATABLE','COMMENTABLE')";

    @Select("SELECT a.* FROM article a JOIN app_user u ON a.owner_id = u.id " +
            "WHERE a.status <> 'failed' AND ( a.share_level IN " + PUBLIC_LEVELS +
            " OR (a.share_level IS NULL AND u.default_share_level IN " + PUBLIC_LEVELS + ") ) " +
            "ORDER BY a.create_time DESC LIMIT #{cap}")
    List<Article> findPublicCandidates(@Param("cap") int cap);

    @Select("SELECT a.* FROM article a JOIN app_user u ON a.owner_id = u.id " +
            "WHERE a.owner_id = #{ownerId} AND a.status <> 'failed' AND ( a.share_level IN " + PUBLIC_LEVELS +
            " OR (a.share_level IS NULL AND u.default_share_level IN " + PUBLIC_LEVELS + ") ) " +
            "ORDER BY a.create_time DESC LIMIT #{cap}")
    List<Article> findPublicByOwner(@Param("ownerId") String ownerId, @Param("cap") int cap);
}
