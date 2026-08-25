package com.loan.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.loan.context.UserContext;
import com.loan.context.LoanUser;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充（参考 tse CustomMetaObjectHandler）。
 *
 * <p>INSERT 填充 created_by/created_at，UPDATE 填充 updated_by/updated_at；
 * <b>created_by/updated_by 存操作人姓名（非 ID）</b>，无登录上下文时留空由数据库默认处理。
 *
 * @author loan-platform
 */
@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        String operator = currentOperatorName();
        if (operator != null) {
            strictInsertFill(metaObject, "createdBy", String.class, operator);
            strictInsertFill(metaObject, "updatedBy", String.class, operator);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        String operator = currentOperatorName();
        if (operator != null) {
            strictUpdateFill(metaObject, "updatedBy", String.class, operator);
        }
    }

    /**
     * 当前操作人姓名（存姓名非 ID）。
     *
     * @return 操作人姓名，无登录上下文返回 null
     */
    private String currentOperatorName() {
        LoanUser user = UserContext.getUser();
        return user == null ? null : user.getName();
    }
}
