package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.client.entity.ClientProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 客户档案 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientProfileMapper extends BaseMapper<ClientProfile> {

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
