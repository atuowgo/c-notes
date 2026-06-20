package com.cnotes.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class User {
    /** 系统初始用户:承接多用户化之前的全部存量数据。匿名/无 token 请求亦回退到此用户。 */
    public static final String SYSTEM_ID = "00000000000000000000000000000001";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String defaultShareLevel;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
