package com.loan.invitation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邀请凭证实体（t_invitation，仅 referrer_type=CUSTOMER 受邀用户推荐进入奖励结算）。
 *
 * <p>referrer_id / used_by_client_id 为多态内部引用（员工/渠道/客户档案自增主键），
 * 仅作内部关联查询，不对外暴露；奖励结算链路在代码中映射为客户编码。
 *
 * @author loan-platform
 */
@Data
@TableName("t_invitation")
public class Invitation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邀请码/短码 */
    private String invitationCode;

    /** 邀请类型（CHANNEL/ENTERPRISE/PERSONAL） */
    private String inviteType;

    /** 引荐人类型（BOSS/ADVISER/CHANNEL/VIP/CUSTOMER） */
    private String referrerType;

    /** 引荐人 ID（员工/渠道账号/客户档案自增主键，内部引用） */
    private Long referrerId;

    /** 引荐人客户编码（仅 referrer_type=CUSTOMER，奖励结算用） */
    private String referrerClientCode;

    /** 场景参数 */
    private String sceneParam;

    /** 有效期 */
    private LocalDateTime expireAt;

    /** 使用状态（注册成功即作废） */
    private Integer usedFlag;

    /** 使用者客户档案 ID（内部引用） */
    private Long usedByClientId;

    /** 使用者客户编码（业务 ID） */
    private String usedByClientCode;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 状态（ACTIVE/VOID） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
