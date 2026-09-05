package com.loan.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.approval.entity.ProductApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品审核工单 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ProductApprovalMapper extends BaseMapper<ProductApproval> {
}
