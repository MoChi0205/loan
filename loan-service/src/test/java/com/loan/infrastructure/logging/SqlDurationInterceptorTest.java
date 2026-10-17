package com.loan.infrastructure.logging;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SqlDurationInterceptorTest {
    private MappedStatement statement() {
        return new MappedStatement.Builder(new Configuration(), "test.Mapper.query",
                parameter -> new BoundSql(new Configuration(), "SELECT ?", Collections.emptyList(), parameter),
                SqlCommandType.SELECT).build();
    }

    @Test void queryOverloadsKeepOriginalResult() throws SQLException {
        Executor delegate = mock(Executor.class);
        MappedStatement statement = statement();
        Object parameter = new Object();
        List<String> result = Collections.singletonList("result");
        when(delegate.query(eq(statement), eq(parameter), eq(RowBounds.DEFAULT), isNull())).thenReturn((List) result);
        when(delegate.query(eq(statement), eq(parameter), eq(RowBounds.DEFAULT), isNull(), any(CacheKey.class), any(BoundSql.class))).thenReturn((List) result);
        Executor proxy = (Executor) new SqlDurationInterceptor().plugin(delegate);
        assertSame(result, proxy.query(statement, parameter, RowBounds.DEFAULT, null));
        assertSame(result, proxy.query(statement, parameter, RowBounds.DEFAULT, null,
                new CacheKey(), statement.getBoundSql(parameter)));
    }

    @Test void failedUpdateDoesNotSwallowOriginalException() throws SQLException {
        Executor delegate = mock(Executor.class);
        SQLException failure = new SQLException("failure");
        when(delegate.update(any(), any())).thenThrow(failure);
        Executor proxy = (Executor) new SqlDurationInterceptor().plugin(delegate);
        assertSame(failure, assertThrows(SQLException.class, () -> proxy.update(statement(), new Object())));
    }
}
