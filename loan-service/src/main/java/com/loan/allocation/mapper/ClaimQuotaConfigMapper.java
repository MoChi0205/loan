package com.loan.allocation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.allocation.entity.ClaimQuotaConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认领配额配置 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClaimQuotaConfigMapper extends BaseMapper<ClaimQuotaConfig> {
}
