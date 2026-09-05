package com.loan.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖励流水实体（t_reward_record，成交落库自动计算入单，可追溯可查账）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_reward_record")
public class RewardRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待审核 */
    public static final String STATUS_PENDING_AUDIT = "PENDING_AUDIT";
    /** 状态：已发放 */
    public static final String STATUS_GRANTED = "GRANTED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已作废 */
    public static final String STATUS_VOID = "VOID";

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 奖励单号（业务唯一ID：reward + 32 位随机） */
    private String rewardNo;

    /** 推荐人客户编码（业务唯一ID） */
    private String referrerClientCode;

    /** 被推荐人客户编码（业务唯一ID） */
    private String refereeClientCode;

    /** 层级（1 直推发奖励 / 2 间推仅记录，封顶 2 层） */
    private Integer level;

    /** 关联工单号（业务唯一ID，成交基数来源） */
    private String serviceOrderNo;

    /** 基数金额快照（成交金额） */
    private BigDecimal baseAmount;

    /** 比例快照 */
    private BigDecimal rateSnapshot;

    /** 规则版本快照 */
    private String ruleVersion;

    /** 计算过程留痕 */
    private String calcProcess;

    /** 奖励金额 */
    private BigDecimal rewardAmount;

    /** 状态（PENDING_AUDIT/GRANTED/REJECTED/VOID） */
    private String status;

    /** 驳回原因（必填） */
    private String rejectReason;

    /** 人工调整标记 */
    private Integer manualAdjustFlag;

    /** 人工调整原因 */
    private String manualAdjustReason;

    /** 结算人工号（业务编码） */
    private String settleStaffCode;

    /** 结算时间 */
    private LocalDateTime settleTime;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
