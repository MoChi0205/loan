package com.loan.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.lead.entity.LeadAllocationRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
    @Insert({"<script>INSERT INTO t_lead_allocation_record "
            + "(lead_no, action_type, from_staff_code, to_staff_code, operator, remark, created_at) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.leadNo}, #{item.actionType}, #{item.fromStaffCode}, #{item.toStaffCode}, #{item.operator}, "
            + "#{item.remark}, #{item.createdAt})",
            "</foreach></script>"})
    int insertBatch(@Param("list") List<LeadAllocationRecord> list);
}
