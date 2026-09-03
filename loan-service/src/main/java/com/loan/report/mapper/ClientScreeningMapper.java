package com.loan.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.report.entity.ClientScreening;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 初筛报告 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientScreeningMapper extends BaseMapper<ClientScreening> {

    /** 渠道本人录入客户的分析报告分页，客户范围在数据库侧 EXISTS 收口。 */
    @Select({"<script>",
            "SELECT cs.* FROM t_client_screening cs",
            "JOIN t_client_profile cp ON cp.client_code = cs.client_profile_code",
            "WHERE EXISTS (SELECT 1 FROM t_lead l",
            " WHERE (l.client_profile_code = cp.client_code OR l.lead_no = cp.lead_no",
            "        OR (l.phone_hash = cp.phone_hash AND l.lead_type = cp.customer_group))",
            " AND l.source = 'CHANNEL'",
            " AND l.follow_status NOT IN ('PENDING_APPROVAL', 'REJECTED')",
            " AND (l.recorder_staff_code = #{channelNo}",
            "      OR JSON_UNQUOTE(JSON_EXTRACT(l.ext_json, '$.recorderChannelNo')) = #{channelNo}))",
            "<if test='status != null and status != \"\"'> AND cs.status = #{status}</if>",
            "<if test='grade != null and grade != \"\"'> AND cs.grade = #{grade}</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            " AND (cs.report_no LIKE CONCAT('%', #{keyword}, '%')",
            "      OR cp.contact_name LIKE CONCAT('%', #{keyword}, '%')",
            "      OR cp.enterprise_name LIKE CONCAT('%', #{keyword}, '%')",
            "      <if test='phoneHash != null and phoneHash != \"\"'> OR cp.phone_hash = #{phoneHash}</if>)",
            "</if>",
            "ORDER BY cs.created_at",
            "<choose><when test='orderDir == \"asc\"'> ASC</when><otherwise> DESC</otherwise></choose>",
            "</script>"})
    Page<ClientScreening> selectChannelOwnedPage(Page<ClientScreening> page,
                                                  @Param("channelNo") String channelNo,
                                                  @Param("status") String status,
                                                  @Param("grade") String grade,
                                                  @Param("keyword") String keyword,
                                                  @Param("phoneHash") String phoneHash,
                                                  @Param("orderDir") String orderDir);

    /** 渠道按报告业务编号批量查询本人可见报告；单次查询避免逐条校验。 */
    @Select({"<script>",
            "SELECT cs.* FROM t_client_screening cs",
            "JOIN t_client_profile cp ON cp.client_code = cs.client_profile_code",
            "WHERE cs.report_no IN",
            "<foreach collection='reportNos' item='code' open='(' separator=',' close=')'>#{code}</foreach>",
            "AND EXISTS (SELECT 1 FROM t_lead l",
            " WHERE (l.client_profile_code = cp.client_code OR l.lead_no = cp.lead_no",
            "        OR (l.phone_hash = cp.phone_hash AND l.lead_type = cp.customer_group))",
            " AND l.source = 'CHANNEL'",
            " AND l.follow_status NOT IN ('PENDING_APPROVAL', 'REJECTED')",
            " AND (l.recorder_staff_code = #{channelNo}",
            "      OR JSON_UNQUOTE(JSON_EXTRACT(l.ext_json, '$.recorderChannelNo')) = #{channelNo}))",
            "</script>"})
    List<ClientScreening> selectChannelOwnedByReportNos(@Param("channelNo") String channelNo,
                                                         @Param("reportNos") Collection<String> reportNos);

    /** 渠道是否拥有指定报告的只读查看范围。 */
    @Select("SELECT COUNT(1) FROM t_client_screening cs "
            + "JOIN t_client_profile cp ON cp.client_code = cs.client_profile_code "
            + "WHERE cs.report_no = #{reportNo} AND EXISTS (SELECT 1 FROM t_lead l "
            + "WHERE (l.client_profile_code = cp.client_code OR l.lead_no = cp.lead_no "
            + "OR (l.phone_hash = cp.phone_hash AND l.lead_type = cp.customer_group)) "
            + "AND l.source = 'CHANNEL' AND l.follow_status NOT IN ('PENDING_APPROVAL', 'REJECTED') "
            + "AND (l.recorder_staff_code = #{channelNo} "
            + "OR JSON_UNQUOTE(JSON_EXTRACT(l.ext_json, '$.recorderChannelNo')) = #{channelNo}))")
    int countChannelOwnedReport(@Param("channelNo") String channelNo,
                                @Param("reportNo") String reportNo);
}
