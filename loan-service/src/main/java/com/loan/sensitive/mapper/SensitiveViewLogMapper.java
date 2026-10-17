package com.loan.sensitive.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.sensitive.entity.SensitiveViewLog;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDate;
@Mapper
public interface SensitiveViewLogMapper extends BaseMapper<SensitiveViewLog> {
    default Long countToday(String userNo, LocalDate date) {
        return selectCount(day(userNo, date));
    }
    default Long countTodayClientViews(String userNo, LocalDate date) {
        return selectList(day(userNo, date).select(SensitiveViewLog::getClientCode)
            .isNotNull(SensitiveViewLog::getClientCode)).stream().map(SensitiveViewLog::getClientCode).distinct().count();
    }
    default Long countTodayClientView(String userNo, LocalDate date, String clientCode) {
        return selectCount(day(userNo, date).eq(SensitiveViewLog::getClientCode, clientCode));
    }
    default LambdaQueryWrapper<SensitiveViewLog> day(String userNo, LocalDate date) {
        return new LambdaQueryWrapper<SensitiveViewLog>().eq(SensitiveViewLog::getUserNo, userNo).eq(SensitiveViewLog::getViewDate, date);
    }
}
