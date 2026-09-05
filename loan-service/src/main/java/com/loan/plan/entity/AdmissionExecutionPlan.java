package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 准入执行计划实体（t_admission_execution_plan）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_admission_execution_plan")
public class AdmissionExecutionPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划编码 */
    private String planCode;

    /** 计划名称 */
    private String planName;

    /** 客群 */
    private String customerGroup;

    /** 版本 */
    private Integer version;

    /** 生命周期（0 草稿 / 1 上线+写锁） */
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
