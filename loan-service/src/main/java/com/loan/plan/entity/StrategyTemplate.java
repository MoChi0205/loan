package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略模版实体（t_strategy_template，对齐 mds 功能策略模版）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_strategy_template")
public class StrategyTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模版编码 */
    private String templateCode;

    /** 模版名称 */
    private String templateName;

    /** 客群 */
    private String customerGroup;

    /** 模版说明 */
    private String description;

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
