package com.loan.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.audit.entity.MatchTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 匹配审计主表 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface MatchTraceMapper extends BaseMapper<MatchTrace> {
}
