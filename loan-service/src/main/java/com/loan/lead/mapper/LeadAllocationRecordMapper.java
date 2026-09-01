package com.loan.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.lead.entity.LeadAllocationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索流转记录 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface LeadAllocationRecordMapper extends BaseMapper<LeadAllocationRecord> {
}
