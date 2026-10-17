package com.loan.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.audit.entity.MatchRuleLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 匹配规则日志 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface MatchRuleLogMapper extends BaseMapper<MatchRuleLog> {

    /**
     * 批量落匹配规则日志（一次多值 INSERT，消除三层嵌套循环内的单条 insert 的 N+1）。
     * 主键由数据库自增生成；调用前需保证 list 非空。
     *
     * @param list 规则日志列表
     * @return 受影响行数
     */
    default int insertBatch(List<MatchRuleLog> list) {
        return com.loan.common.util.MapperAccess.insertBatch(MatchRuleLog.class, MatchRuleLogMapper.class, list);
    }
}
