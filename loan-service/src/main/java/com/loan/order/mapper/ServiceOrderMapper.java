package com.loan.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.order.entity.ServiceOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 服务工单 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {
}
