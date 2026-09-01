package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 计划模块实体（t_admission_plan_module）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_admission_plan_module")
public class AdmissionPlanModule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划 ID */
    private Long planId;

    /** 模块编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块逻辑（AND/OR） */
    private String logicType;

    /** 全局前置风控模块（命中直接 REJECT） */
    private Integer isGlobalPre;

    /** 模块顺序 */
    private Integer sort;

    /** 与下一模块连接（AND/OR，支持模块间 OR 组短路） */
    private String joinWithNextModule;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
