package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品准入配置实体（t_product_admission_config，产品 ↔ 计划绑定）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_product_admission_config")
public class ProductAdmissionConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 银行产品 ID */
    private Long bankProductId;

    /** 准入执行计划 ID */
    private Long executionPlanId;

    /** 产品级叠加风控模块 JSON */
    private String riskExtraJson;

    /** 优先级 */
    private Integer priority;

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
