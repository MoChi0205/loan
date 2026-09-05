package com.loan.apiperm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色 × 接口 授权（t_role_api）。
 *
 * <p>BOSS 为业务默认全量角色不落库（系统配置域仍显式拒绝）；
 * DEPT_MANAGER / ADVISER 等精细角色在此表逐接口授权。
 *
 * @author loan-platform
 */
@Data
@TableName("t_role_api")
public class RoleApi {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（DEPT_MANAGER/ADVISER/CHANNEL 等） */
    private String roleCode;

    /** 接口权限键（对应 t_api_permission.api_key） */
    private String apiKey;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
