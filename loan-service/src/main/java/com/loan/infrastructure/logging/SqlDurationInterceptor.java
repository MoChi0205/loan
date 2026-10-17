package com.loan.infrastructure.logging;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;

/** 所有 Wrapper 查询/更新的耗时；只记 statement 名称，禁止输出 SQL 参数和敏感原值。 */
@Slf4j
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class}),
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "flushStatements", args = {})
})
public class SqlDurationInterceptor implements Interceptor {
    @Override public Object intercept(Invocation invocation) throws Throwable {
        long start = System.nanoTime();
        boolean success = false;
        String statement = invocation.getArgs().length == 0 ? "JDBC_BATCH_FLUSH"
                : ((MappedStatement) invocation.getArgs()[0]).getId();
        try {
            Object result = invocation.proceed(); success = true; return result;
        } finally {
            long cost = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (cost >= 1000) log.warn("SQL执行 statement={} operation={} cost={}ms success={}", statement, invocation.getMethod().getName(), cost, success);
            else log.info("SQL执行 statement={} operation={} cost={}ms success={}", statement, invocation.getMethod().getName(), cost, success);
        }
    }
}
