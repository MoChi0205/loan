package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.client.entity.ClientRecycleConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户回收规则配置 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientRecycleConfigMapper extends BaseMapper<ClientRecycleConfig> {
}
