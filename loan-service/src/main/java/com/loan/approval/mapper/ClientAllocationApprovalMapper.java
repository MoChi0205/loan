package com.loan.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.approval.entity.ClientAllocationApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户归属分配审批单 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientAllocationApprovalMapper extends BaseMapper<ClientAllocationApproval> {
}
