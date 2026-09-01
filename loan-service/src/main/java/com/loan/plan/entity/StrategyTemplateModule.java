package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略模版模块实体（t_strategy_template_module）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_strategy_template_module")
public class StrategyTemplateModule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略模版 ID */
    private Long templateId;

    /** 模块编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块逻辑（AND/OR） */
    private String logicType;

    /** 与下一模块连接（AND/OR，支持模块间 OR 组短路） */
    private String joinWithNextModule;

    /** 顺序 */
    private Integer sort;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
