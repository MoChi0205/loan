package com.loan.common.util;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import org.apache.ibatis.logging.LogFactory;
import java.util.Collection;
import java.util.function.Function;

/** 复用当前事务的 Wrapper 跨实体查询与 JDBC 批处理，不拼接业务 SQL。 */
public final class MapperAccess {
    private MapperAccess() { }
    public static <T, R> R query(Class<T> entity, Function<BaseMapper<T>, R> action) {
        return SqlHelper.execute(entity, mapper -> action.apply(mapper));
    }
    public static <T> int insertBatch(Class<T> entity, Class<?> mapper, Collection<T> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        SqlHelper.executeBatch(entity, LogFactory.getLog(MapperAccess.class), rows, 200,
                (session, row) -> session.insert(SqlHelper.getSqlStatement(mapper, SqlMethod.INSERT_ONE), row));
        return rows.size();
    }
}
