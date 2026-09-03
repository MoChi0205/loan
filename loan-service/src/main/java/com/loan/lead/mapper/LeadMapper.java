package com.loan.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.lead.entity.Lead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 线索 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface LeadMapper extends BaseMapper<Lead> {

    /** 渠道线索终审条件更新，防止并发重复审批产生不同结果。 */
    @Update("UPDATE t_lead SET follow_status = #{targetStatus}, updated_by = #{operator}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE lead_no = #{leadNo} AND follow_status = 'PENDING_APPROVAL'")
    int auditChannelLead(@Param("leadNo") String leadNo,
                         @Param("targetStatus") String targetStatus,
                         @Param("operator") String operator);
}
