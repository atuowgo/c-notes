package com.cnotes.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cnotes.auth.entity.AuthIdentity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthIdentityMapper extends BaseMapper<AuthIdentity> {
}
