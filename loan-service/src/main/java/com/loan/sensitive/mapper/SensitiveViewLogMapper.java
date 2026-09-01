package com.loan.sensitive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.sensitive.entity.SensitiveViewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 敏感数据查看留痕 Mapper（日限额 30/天统计依据）。
 *
 * @author loan-platform
 */
@Mapper
public interface SensitiveViewLogMapper extends BaseMapper<SensitiveViewLog> {

    /**
     * 统计某员工当日查看次数。
     *
     * @param userNo   查看人工号
     * @param viewDate 查看日期
     * @return 当日查看次数
     */
    @Select("SELECT COUNT(1) FROM t_sensitive_view_log "
            + "WHERE user_no = #{userNo} AND view_date = #{viewDate}")
    Long countToday(@Param("userNo") String userNo, @Param("viewDate") LocalDate viewDate);
}
