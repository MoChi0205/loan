package com.loan.invitation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.invitation.entity.Invitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 邀请凭证 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface InvitationMapper extends BaseMapper<Invitation> {

    /** 原子消费邀请凭证，防止并发请求将同一码绑定给不同客户。 */
    @Update("UPDATE t_invitation SET used_flag = 1, used_by_client_id = #{clientId}, "
            + "used_by_client_code = #{clientCode}, used_at = #{usedAt} "
            + "WHERE invitation_code = #{inviteCode} AND status = 'ACTIVE' AND used_flag = 0 "
            + "AND (expire_at IS NULL OR expire_at >= #{usedAt})")
    int consume(@Param("inviteCode") String inviteCode,
                @Param("clientId") Long clientId,
                @Param("clientCode") String clientCode,
                @Param("usedAt") LocalDateTime usedAt);
}
