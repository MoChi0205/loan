package com.loan.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.lead.entity.LeadAllocationRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 线索流转记录 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface LeadAllocationRecordMapper extends BaseMapper<LeadAllocationRecord> {

    /**
     * 批量落线索流转记录（一次多值 INSERT，消除循环单条 insert 的 N+1）。
     * 主键由数据库自增生成；调用前需保证 list 非空。
     *
     * @param list 流转记录列表
     * @return 受影响行数
     */
    default int insertBatch(List<LeadAllocationRecord> list) {
        return com.loan.common.util.MapperAccess.insertBatch(LeadAllocationRecord.class, LeadAllocationRecordMapper.class, list);
    }
}
