package com.loan.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.audit.entity.MatchRuleLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 匹配规则日志 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface MatchRuleLogMapper extends BaseMapper<MatchRuleLog> {
}
