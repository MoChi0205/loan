package com.loan.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体（t_operation_log，产品/规则/计划配置变更 + 渠道操作全留痕，保留 ≥3 年）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_operation_log")
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型（产品/规则/计划/配置变更/渠道操作） */
    private String bizType;

    /** 业务对象 ID */
    private String bizId;

    /** 操作动作（CREATE/UPDATE/DELETE/APPROVE/ENABLE 等） */
    private String action;

    /** 变更明细快照 */
    private String detailJson;

    /** 操作人姓名 */
    private String operator;

    /** 操作人角色 */
    private String operatorRole;

    /** IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
