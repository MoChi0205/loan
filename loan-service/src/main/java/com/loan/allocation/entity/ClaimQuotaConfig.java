package com.loan.allocation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 认领配额配置（每日公海认领上限）。
 *
 * <p>参考 tse 的 {@code t_enterprise_allocation_config.daily_assign_limit_per_user}
 * （企业级「每坐席每日公海认领/分配上限」）。本项目为单租户，改用 {@code scope}
 * 分行承载两套资源池：{@code LEAD}（线索公海认领）、{@code CLIENT}（客户公海认领）。</p>
 *
 * <p>与 {@code t_lead_recycle_config} / {@code t_client_recycle_config} 同构：
 * <b>全参数化不写死</b>，改上限只需改数据行，无需改代码或重新发版。
 * {@code daily_claim_limit = 0}（或配置行缺失）表示不限制。</p>
 *
 * @author loan-platform
 */
@Data
@TableName("t_allocation_quota_config")
public class ClaimQuotaConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 适用范围（LEAD 线索认领 / CLIENT 客户认领） */
    private String scope;

    /** 每员工每日公海认领上限（0 = 不限） */
    private Integer dailyClaimLimit;

    /** 每员工持有上限（0 = 不限）；参照 tse max_new_customers_per_user */
    private Integer maxHolding;

    /** 数据保护期天数：进入公海后 N 天内所有人不可认领（0/NULL = 不启用）；参照 tse claim_protect_days */
    private Integer protectDays;

    /** 每日认领冷却条数：与每日认领上限取较小值生效（0/NULL = 不启用） */
    private Integer dailyCooldownLimit;

    /** 退回/回收回纳目标：PUBLIC=公司公海（默认）/ ORIGINAL=归属人原部门团队池 */
    private String returnTarget;

    /** 说明 */
    private String remark;

    /** 更新人姓名 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
