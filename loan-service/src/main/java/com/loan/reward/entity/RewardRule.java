package com.loan.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐奖励规则实体（t_reward_rule，比例快照冻结，后台全可配不写死）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_reward_rule")
public class RewardRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则版本（冻结快照，改比例不影响已生成奖励单） */
    private String ruleVersion;

    /** 直推比例（第 1 层 X%） */
    private BigDecimal directRate;

    /** 间推比例（第 2 层 Y%，默认关） */
    private BigDecimal indirectRate;

    /** 间推开关（默认关闭，发 1 层存 2 层） */
    private Integer indirectEnabled;

    /** 基数口径（SERVICE_ORDER_DEAL：服务工单成交） */
    private String baseCaliber;

    /** 适用产品编码（精确匹配；NULL 视为全局默认） */
    private String productCode;

    /** 产品名称（查询视图字段，不落库）。 */
    @TableField(exist = false)
    private String productName;

    /** 适用客群（ENTERPRISE/PERSONAL；与 productCode 组合精确匹配；NULL 视为全局默认） */
    private String customerGroup;

    /** 生效时间 */
    private LocalDateTime validFrom;

    /** 失效时间 */
    private LocalDateTime validUntil;

    /** 奖励下限（元） */
    private BigDecimal minAmount;

    /** 奖励上限（元） */
    private BigDecimal maxAmount;

    /** 状态（ACTIVE/DISABLED） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
