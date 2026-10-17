package com.loan.invitation.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.invitation.entity.Invitation;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
@Mapper
public interface InvitationMapper extends BaseMapper<Invitation> {
    default int consume(String inviteCode, Long clientId, String clientCode, LocalDateTime usedAt) {
        return update(null, new LambdaUpdateWrapper<Invitation>().eq(Invitation::getInvitationCode, inviteCode)
            .eq(Invitation::getStatus, "ACTIVE").eq(Invitation::getUsedFlag, 0)
            .and(w -> w.isNull(Invitation::getExpireAt).or().ge(Invitation::getExpireAt, usedAt))
            .set(Invitation::getUsedFlag, 1).set(Invitation::getUsedByClientId, clientId)
            .set(Invitation::getUsedByClientCode, clientCode).set(Invitation::getUsedAt, usedAt));
    }
}
