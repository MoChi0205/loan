package com.loan.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.loan.infrastructure.security.AesTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 员工映射实体（t_staff，CRM SSO 员工 → 部门 → 角色三级绑定，不重复建账号）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_staff")
public class Staff implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** loan 内部工号 */
    private String staffCode;

    /** CRM 员工 ID（SSO 映射键） */
    private String crmUserId;

    /** 员工姓名 */
    private String staffName;

    /** 所属部门编码（业务编码） */
    private String deptCode;

    /** 角色（BOSS/DEPT_MANAGER/ADVISER） */
    private String roleCode;

    /** 个人企微二维码 */
    private String wecomQrCode;

    /** 手机号（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String phone;

    /** 手机号 SHA-256 哈希 */
    private String phoneHash;

    /** 状态（ACTIVE/LEAVE） */
    private String status;

    /** 离职时间 */
    private LocalDateTime leaveTime;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
