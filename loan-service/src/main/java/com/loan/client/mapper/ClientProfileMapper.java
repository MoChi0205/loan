package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.client.entity.ClientProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 客户档案 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientProfileMapper extends BaseMapper<ClientProfile> {

    /**
     * 计算客户经营标签的真实业务状态。标签不写死在客户主表，预约/来访/成交随业务记录变化自动生效。
     */
    @Select({"<script>",
            "SELECT cp.client_code AS clientCode,",
            "CASE WHEN EXISTS (SELECT 1 FROM t_client_appointment a WHERE a.client_code = cp.client_code",
            " AND a.status IN ('REQUESTED','CONFIRMED','ARRIVED','SERVING','COMPLETED')) THEN 1 ELSE 0 END AS appointed,",
            "CASE WHEN EXISTS (SELECT 1 FROM t_client_appointment a WHERE a.client_code = cp.client_code",
            " AND (a.actual_arrived_at IS NOT NULL OR a.status IN ('ARRIVED','SERVING','COMPLETED'))) THEN 1 ELSE 0 END AS visited,",
            "CASE WHEN EXISTS (SELECT 1 FROM t_service_order o WHERE o.client_profile_code = cp.client_code",
            " AND o.status = 'DEAL') THEN 1 ELSE 0 END AS deal",
            "FROM t_client_profile cp WHERE cp.client_code IN",
            "<foreach collection='clientCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>",
            "</script>"})
    List<Map<String, Object>> selectDynamicTags(@Param("clientCodes") Collection<String> clientCodes);

    /** 按当前数据范围统计标签数量；动态业务标签与人工标签可重叠计数。 */
    @Select({"<script>",
            "SELECT COUNT(1) AS total,",
            "SUM(CASE WHEN EXISTS (SELECT 1 FROM t_client_appointment a WHERE a.client_code=cp.client_code AND a.status IN ('REQUESTED','CONFIRMED','ARRIVED','SERVING','COMPLETED')) THEN 1 ELSE 0 END) AS appointedCount,",
            "SUM(CASE WHEN EXISTS (SELECT 1 FROM t_client_appointment a WHERE a.client_code=cp.client_code AND (a.actual_arrived_at IS NOT NULL OR a.status IN ('ARRIVED','SERVING','COMPLETED'))) THEN 1 ELSE 0 END) AS visitedCount,",
            "SUM(CASE WHEN EXISTS (SELECT 1 FROM t_service_order o WHERE o.client_profile_code=cp.client_code AND o.status='DEAL') THEN 1 ELSE 0 END) AS dealCount,",
            "SUM(CASE WHEN cp.customer_tag='NEW' OR cp.customer_tag IS NULL OR cp.customer_tag='' THEN 1 ELSE 0 END) AS newCount,",
            "SUM(CASE WHEN cp.customer_tag='INTENTION' THEN 1 ELSE 0 END) AS intentionCount,",
            "SUM(CASE WHEN cp.customer_tag='POTENTIAL' THEN 1 ELSE 0 END) AS potentialCount,",
            "SUM(CASE WHEN cp.customer_tag='NO_ANSWER' THEN 1 ELSE 0 END) AS noAnswerCount,",
            "SUM(CASE WHEN cp.customer_tag='NO_NEED' THEN 1 ELSE 0 END) AS noNeedCount",
            "FROM t_client_profile cp WHERE 1=1",
            "<if test=\"ownerStaffCode != null and ownerStaffCode != ''\"> AND cp.owner_staff_code=#{ownerStaffCode}</if>",
            "<if test=\"ownerDeptCode != null and ownerDeptCode != ''\"> AND cp.owner_staff_code IN (SELECT s.staff_code FROM t_staff s WHERE s.status='ACTIVE' AND s.dept_code=#{ownerDeptCode})</if>",
            "<if test='assignedOnly'> AND cp.owner_staff_code IS NOT NULL</if>",
            "</script>"})
    Map<String, Object> selectTagCounts(@Param("ownerStaffCode") String ownerStaffCode,
                                         @Param("ownerDeptCode") String ownerDeptCode,
                                         @Param("assignedOnly") boolean assignedOnly);

    /** 受控手机号查看专用读取；普通查询不会选择 phone_plain。 */
    @Select("SELECT phone_plain FROM t_client_profile WHERE client_code = #{clientCode} LIMIT 1")
    String selectPhonePlain(@Param("clientCode") String clientCode);

    @Update("UPDATE t_client_profile SET phone_plain = #{phonePlain} WHERE client_code = #{clientCode} AND (phone_plain IS NULL OR phone_plain = '')")
    int backfillPhonePlain(@Param("clientCode") String clientCode, @Param("phonePlain") String phonePlain);

    /**
     * 渠道本人录入并已转化的客户分页。录入主体使用渠道业务编号，兼容历史 ext_json 记录。
     */
    @Select({"<script>",
            "SELECT cp.* FROM t_client_profile cp",
            "WHERE EXISTS (SELECT 1 FROM t_lead l",
            " WHERE (l.client_profile_code = cp.client_code OR l.lead_no = cp.lead_no",
            "        OR (l.phone_hash = cp.phone_hash AND l.lead_type = cp.customer_group))",
            " AND l.source = 'CHANNEL'",
            " AND l.follow_status NOT IN ('PENDING_APPROVAL', 'REJECTED')",
            " AND (l.recorder_staff_code = #{channelNo}",
            "      OR JSON_UNQUOTE(JSON_EXTRACT(l.ext_json, '$.recorderChannelNo')) = #{channelNo}))",
            "<if test='keyword != null and keyword != \"\"'>",
            " AND (cp.contact_name LIKE CONCAT('%', #{keyword}, '%')",
            "      OR cp.enterprise_name LIKE CONCAT('%', #{keyword}, '%')",
            "      <if test='phoneHash != null and phoneHash != \"\"'> OR cp.phone_hash = #{phoneHash}</if>)",
            "</if>",
            "ORDER BY cp.created_at",
            "<choose><when test='orderDir == \"asc\"'> ASC</when><otherwise> DESC</otherwise></choose>",
            "</script>"})
    Page<ClientProfile> selectChannelOwnedPage(Page<ClientProfile> page,
                                                @Param("channelNo") String channelNo,
                                                @Param("keyword") String keyword,
                                                @Param("phoneHash") String phoneHash,
                                                @Param("orderDir") String orderDir);

    /** 渠道按客户业务编码批量查询本人可见客户；单次查询避免逐条校验。 */
    @Select({"<script>",
            "SELECT cp.* FROM t_client_profile cp",
            "WHERE cp.client_code IN",
            "<foreach collection='clientCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>",
            "AND EXISTS (SELECT 1 FROM t_lead l",
            " WHERE (l.client_profile_code = cp.client_code OR l.lead_no = cp.lead_no",
            "        OR (l.phone_hash = cp.phone_hash AND l.lead_type = cp.customer_group))",
            " AND l.source = 'CHANNEL'",
            " AND l.follow_status NOT IN ('PENDING_APPROVAL', 'REJECTED')",
            " AND (l.recorder_staff_code = #{channelNo}",
            "      OR JSON_UNQUOTE(JSON_EXTRACT(l.ext_json, '$.recorderChannelNo')) = #{channelNo}))",
            "</script>"})
    List<ClientProfile> selectChannelOwnedByCodes(@Param("channelNo") String channelNo,
                                                   @Param("clientCodes") Collection<String> clientCodes);

    /** 渠道是否拥有指定客户的只读查看范围。 */
    @Select("SELECT COUNT(1) FROM t_client_profile cp WHERE cp.client_code = #{clientCode} "
            + "AND EXISTS (SELECT 1 FROM t_lead l "
            + "WHERE (l.client_profile_code = cp.client_code OR l.lead_no = cp.lead_no "
            + "OR (l.phone_hash = cp.phone_hash AND l.lead_type = cp.customer_group)) "
            + "AND l.source = 'CHANNEL' AND l.follow_status NOT IN ('PENDING_APPROVAL', 'REJECTED') "
            + "AND (l.recorder_staff_code = #{channelNo} "
            + "OR JSON_UNQUOTE(JSON_EXTRACT(l.ext_json, '$.recorderChannelNo')) = #{channelNo}))")
    int countChannelOwnedClient(@Param("channelNo") String channelNo,
                                @Param("clientCode") String clientCode);

    /** 客户仍未分配时原子写入服务顾问，防止并发审批覆盖已生效归属。 */
    @Update("UPDATE t_client_profile SET owner_staff_code = #{staffCode}, "
            + "sea_level = NULL, sea_dept_code = NULL, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} "
            + "WHERE client_code = #{clientCode} AND owner_staff_code IS NULL")
    int assignOwnerIfUnassigned(@Param("clientCode") String clientCode,
                                @Param("staffCode") String staffCode,
                                @Param("updatedBy") String updatedBy,
                                @Param("updatedAt") LocalDateTime updatedAt);

    /** 管理者直接分配：仅当归属仍等于读取时的值才更新，避免覆盖并发变更。 */
    @Update("UPDATE t_client_profile SET owner_staff_code = #{staffCode}, "
            + "sea_level = NULL, sea_dept_code = NULL, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} "
            + "WHERE client_code = #{clientCode} "
            + "AND ((owner_staff_code = #{expectedOwner}) OR (owner_staff_code IS NULL AND #{expectedOwner} IS NULL))")
    int assignOwnerIfUnchanged(@Param("clientCode") String clientCode,
                               @Param("staffCode") String staffCode,
                               @Param("expectedOwner") String expectedOwner,
                               @Param("updatedBy") String updatedBy,
                               @Param("updatedAt") LocalDateTime updatedAt);

    /** 转移审批通过：仅当客户仍归属申请时的原顾问才允许转移。 */
    @Update("UPDATE t_client_profile SET owner_staff_code = #{staffCode}, "
            + "sea_level = NULL, sea_dept_code = NULL, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} "
            + "WHERE client_code = #{clientCode} AND owner_staff_code = #{expectedOwner}")
    int transferOwnerIfUnchanged(@Param("clientCode") String clientCode,
                                 @Param("staffCode") String staffCode,
                                 @Param("expectedOwner") String expectedOwner,
                                 @Param("updatedBy") String updatedBy,
                                 @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 按微信 openid SHA-256 哈希等值查询客户档案（方案 A 登录链路）。
     *
     * @param wxOpenidHash openid 哈希
     * @return 客户档案，不存在返回 null
     */
    default ClientProfile selectByWxOpenidHash(String wxOpenidHash) {
        if (wxOpenidHash == null || wxOpenidHash.isEmpty()) {
            return null;
        }
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getWxOpenidHash, wxOpenidHash)
                .last("limit 1"));
    }
}
