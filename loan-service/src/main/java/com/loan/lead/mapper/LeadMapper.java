package com.loan.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.lead.entity.Lead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

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

    /** 公海认领条件更新；只有当前仍无归属的线索可成功认领。 */
    @Update("UPDATE t_lead SET owner_staff_code = #{staffCode}, updated_by = #{staffName}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE lead_no = #{leadNo} AND owner_staff_code IS NULL")
    int claimIfUnowned(@Param("leadNo") String leadNo,
                       @Param("staffCode") String staffCode,
                       @Param("staffName") String staffName);

    /** 按业务编码批量删除，调用方须先完成权限与审计校验。 */
    @Delete({"<script>DELETE FROM t_lead WHERE lead_no IN ",
            "<foreach collection='leadNos' item='no' open='(' separator=',' close=')'>#{no}</foreach>",
            "</script>"})
    int deleteByLeadNos(@Param("leadNos") java.util.List<String> leadNos);

    /** 管理员按业务编码批量指派到同一顾问。 */
    @Update({"<script>UPDATE t_lead SET owner_staff_code = #{staffCode}, updated_by = #{operator}, ",
            "updated_at = CURRENT_TIMESTAMP WHERE lead_no IN ",
            "<foreach collection='leadNos' item='no' open='(' separator=',' close=')'>#{no}</foreach>",
            "</script>"})
    int assignByLeadNos(@Param("leadNos") java.util.List<String> leadNos,
                        @Param("staffCode") String staffCode,
                        @Param("operator") String operator);
}
