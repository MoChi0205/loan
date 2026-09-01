package com.loan.invitation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.invitation.entity.Invitation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邀请凭证 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface InvitationMapper extends BaseMapper<Invitation> {
}
