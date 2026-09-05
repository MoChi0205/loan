package com.loan.lead.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.loan.infrastructure.security.AesTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线索主表实体（t_lead，唯一入口；归属/回收/公海/流转只作用主表）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_lead")
public class Lead implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 线索编号 */
    private String leadNo;

    /** 客群（ENTERPRISE/PERSONAL） */
    private String leadType;

    /** 联系人 */
    private String contactName;

    /** 手机号（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String phone;

    /** 手机号 SHA-256 哈希 */
    private String phoneHash;

    /** 来源（BOSS/ADVISER/CHANNEL/VIP） */
    private String source;

    /** 录入人工号（业务编码，公司员工时 = 归属人） */
    private String recorderStaffCode;

    /** 归属人工号（业务编码，NULL = 公海） */
    private String ownerStaffCode;

    /** 跟进状态（NEW/INTENTION/POTENTIAL/VISITED/NO_ANSWER/NO_NEED） */
    private String followStatus;

    /** 最后跟进时间（回收扫描依据） */
    private LocalDateTime lastFollowedAt;

    /** 认领冷却截止（回收后冷却期内原归属人不可认领） */
    private LocalDateTime assignBlockedUntil;

    /** 转正客户编码（业务唯一ID，认证通过后转正） */
    private String clientProfileCode;

    /** 扩展预留 JSON */
    private String extJson;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
