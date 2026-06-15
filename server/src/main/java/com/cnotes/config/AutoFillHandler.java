package com.cnotes.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class AutoFillHandler implements MetaObjectHandler {
    @Override public void insertFill(MetaObject m) {
        strictInsertFill(m, "createTime", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
    @Override public void updateFill(MetaObject m) {
        strictUpdateFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
