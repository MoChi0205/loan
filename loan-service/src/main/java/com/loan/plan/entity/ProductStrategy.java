package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道准入策略实体（t_product_strategy，细粒度：渠道 × 产品 × 客群 → 计划 1:1）。
 *
 * <p>对齐 mds v2「渠道 → 策略 → 计划」三层；策略承载渠道/产品/客群三维度，
 * 1:1 绑定一个执行计划（execution_plan_code 唯一）。
 *
 * <p>数据库规范：关联一律使用业务 key 字符串（channel_code / bank_product_code /
 * execution_plan_code），不建物理外键、不使用自增 id 跨表关联；主键自增 id 仅作行标识。
 *
 * @author loan-platform
 */
@Data
@TableName("t_product_strategy")
public class ProductStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（仅行标识，禁止用于跨表关联） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码（业务 key，t_bank_channel.channel_code） */
    private String channelCode;

    /** 银行产品编码（业务 key，t_bank_product.product_code；产品挂策略） */
    private String bankProductCode;

    /** 客群（ENTERPRISE/PERSONAL/COMMON） */
    private String customerGroup;

    /** 策略编码（渠道内唯一） */
    private String strategyCode;

    /** 策略名称 */
    private String strategyName;

    /** 策略说明 */
    private String description;

    /** 执行计划编码（业务 key，t_admission_execution_plan.plan_code；1:1） */
    private String executionPlanCode;

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
