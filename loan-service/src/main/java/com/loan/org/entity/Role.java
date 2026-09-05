package com.loan.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色实体（t_role，三级角色：BOSS/DEPT_MANAGER/ADVISER）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_role")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（大写） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色说明 */
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
