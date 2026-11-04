package com.example.tcms.config;

import java.time.Clock;
import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import lombok.RequiredArgsConstructor;

/**
 * 自动填充 created_at / updated_at。
 * <p>
 * 实体字段需标注 {@code @TableField(fill = FieldFill.INSERT)} / {@code FieldFill.INSERT_UPDATE}。
 * 注意：仅通过实体执行的 insert/update 会触发；{@code update(null, wrapper)} 这种无实体更新需要自行 set updated_at。
 */
@Component
@RequiredArgsConstructor
public class AuditFieldHandler implements MetaObjectHandler {

    private final Clock clock;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(clock);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // strictUpdateFill 只在字段为 null 时填充；先查后改的实体 updatedAt 非空，因此这里强制覆盖
        if (metaObject.hasSetter("updatedAt")) {
            setFieldValByName("updatedAt", LocalDateTime.now(clock), metaObject);
        }
    }
}
