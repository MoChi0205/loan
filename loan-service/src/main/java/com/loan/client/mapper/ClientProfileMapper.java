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

/**
 * 客户档案 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientProfileMapper extends BaseMapper<ClientProfile> {

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
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} "
            + "WHERE client_code = #{clientCode} AND owner_staff_code IS NULL")
    int assignOwnerIfUnassigned(@Param("clientCode") String clientCode,
                                @Param("staffCode") String staffCode,
                                @Param("updatedBy") String updatedBy,
                                @Param("updatedAt") LocalDateTime updatedAt);

    /** 管理者直接分配：仅当归属仍等于读取时的值才更新，避免覆盖并发变更。 */
    @Update("UPDATE t_client_profile SET owner_staff_code = #{staffCode}, "
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
