package com.loan.blacklist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 风控黑名单实体（t_blacklist，全局前置风控命中直接 REJECT）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_blacklist")
public class Blacklist implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 命中维度（PHONE/ID_CARD/CREDIT_CODE/LEGAL_PERSON） */
    private String dimension;

    /** 命中值（AES 加密） */
    private String value;

    /** 命中值 SHA-256 哈希（匹配用） */
    private String valueHash;

    /** 原因分类（FRAUD/DISHONEST/SENSITIVE/OTHER） */
    private String reasonType;

    /** 原因说明 */
    private String reasonRemark;

    /** 状态（EFFECTIVE 生效 / RELEASED 已解禁） */
    private String status;

    /** 解禁人工号（业务编码） */
    private String releaseStaffCode;

    /** 解禁时间 */
    private LocalDateTime releaseTime;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
