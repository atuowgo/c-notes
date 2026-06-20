package com.cnotes.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_identity")
public class AuthIdentity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String provider;      // github / google / wechat(email 预留)
    private String providerUid;   // GitHub id / Google sub / 微信 openid
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
