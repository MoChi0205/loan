package com.loan.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
