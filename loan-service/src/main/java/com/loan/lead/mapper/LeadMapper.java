package com.loan.lead.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.lead.entity.Lead;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;
/** 按业务编码条件更新；并发守卫保留在 Wrapper 条件中。 */
@Mapper
public interface LeadMapper extends BaseMapper<Lead> {
    default int auditChannelLead(String leadNo, String targetStatus, String operator) {
        return update(null, new LambdaUpdateWrapper<Lead>().eq(Lead::getLeadNo, leadNo)
            .eq(Lead::getFollowStatus, "PENDING_APPROVAL").set(Lead::getFollowStatus, targetStatus)
            .set(Lead::getUpdatedBy, operator).set(Lead::getUpdatedAt, LocalDateTime.now()));
    }
    default int bindClientProfile(String leadNo, String clientCode, String operator) {
        return update(null, new LambdaUpdateWrapper<Lead>().eq(Lead::getLeadNo, leadNo)
            .set(Lead::getClientProfileCode, clientCode).set(Lead::getUpdatedBy, operator).set(Lead::getUpdatedAt, LocalDateTime.now()));
    }
    default int claimIfUnowned(String leadNo, String staffCode, String staffName) {
        return update(null, new LambdaUpdateWrapper<Lead>().eq(Lead::getLeadNo, leadNo).isNull(Lead::getOwnerStaffCode)
            .set(Lead::getOwnerStaffCode, staffCode)
            .set(Lead::getSeaLevel, null).set(Lead::getSeaDeptCode, null)
            .set(Lead::getUpdatedBy, staffName).set(Lead::getUpdatedAt, LocalDateTime.now()));
    }
    default int releaseOwned(String leadNo, String staffCode, String staffName, LocalDateTime blockedUntil,
                             String seaLevel, String seaDeptCode) {
        return update(null, new LambdaUpdateWrapper<Lead>().eq(Lead::getLeadNo, leadNo).eq(Lead::getOwnerStaffCode, staffCode)
            .set(Lead::getOwnerStaffCode, null).set(Lead::getAssignBlockedUntil, blockedUntil)
            .set(Lead::getSeaLevel, seaLevel).set(Lead::getSeaDeptCode, seaDeptCode)
            .set(Lead::getUpdatedBy, staffName).set(Lead::getUpdatedAt, LocalDateTime.now()));
    }
    default int followOwned(String leadNo, String staffCode, String followStatus, LocalDateTime followedAt, String operator) {
        return update(null, new LambdaUpdateWrapper<Lead>().eq(Lead::getLeadNo, leadNo).eq(Lead::getOwnerStaffCode, staffCode)
            .set(Lead::getFollowStatus, followStatus).set(Lead::getLastFollowedAt, followedAt)
            .set(Lead::getUpdatedAt, followedAt).set(Lead::getUpdatedBy, operator));
    }
    default int deleteByLeadNos(List<String> leadNos) {
        return leadNos == null || leadNos.isEmpty() ? 0 : delete(new LambdaQueryWrapper<Lead>().in(Lead::getLeadNo, leadNos));
    }
    default int assignByLeadNos(List<String> leadNos, String staffCode, String operator) {
        return leadNos == null || leadNos.isEmpty() ? 0 : update(null, new LambdaUpdateWrapper<Lead>().in(Lead::getLeadNo, leadNos)
            .set(Lead::getOwnerStaffCode, staffCode).set(Lead::getUpdatedBy, operator).set(Lead::getUpdatedAt, LocalDateTime.now()));
    }
    /** 真正清空归属，显式 set(null) 不受实体 NOT_NULL 更新策略影响。 */
    default int recycleToPool(List<Long> ids, LocalDateTime blockedUntil) {
        return ids == null || ids.isEmpty() ? 0 : update(null, new LambdaUpdateWrapper<Lead>().in(Lead::getId, ids)
            .set(Lead::getOwnerStaffCode, null).set(Lead::getAssignBlockedUntil, blockedUntil)
            .set(Lead::getUpdatedBy, "system").set(Lead::getUpdatedAt, LocalDateTime.now()));
    }
}
