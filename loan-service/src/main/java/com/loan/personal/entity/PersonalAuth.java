package com.loan.personal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人认证记录实体（t_personal_auth）。
 *
 * <p>认证类型：ID_CARD_OCR 身份证 OCR / FACE_LIVENESS 人脸活体 / PHONE_THREE_ELEMENT 手机三要素；
 * Mock 模式下 auth_status=SUCCESS、fail_reason 标注 MOCK（Q5 决策）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_personal_auth")
public class PersonalAuth implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编码（业务ID） */
    private String clientProfileCode;

    /** 认证类型（ID_CARD_OCR / FACE_LIVENESS / PHONE_THREE_ELEMENT） */
    private String authType;

    /** 认证状态（PENDING / SUCCESS / FAIL） */
    private String authStatus;

    /** 失败原因 */
    private String failReason;

    /** 认证时间 */
    private LocalDateTime authTime;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
