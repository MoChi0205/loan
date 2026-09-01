package com.loan.apiperm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口权限定义（网关鉴权清单，运行时由 ApiPermissionSyncService 自动同步）。
 *
 * <p>每行 = 一个后端接口：api_key（模块:方法名）唯一；http_method + path_pattern 供网关匹配；
 * client_types 声明该接口可在哪些端（WEB / MINI_APP）访问，是「端维度」鉴权的依据。
 *
 * @author loan-platform
 */
@Data
@TableName("t_api_permission")
public class ApiPermission {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接口权限键（模块:方法名，如 order:page） */
    private String apiKey;

    /** HTTP 方法（GET/POST/PUT/DELETE/ALL） */
    private String httpMethod;

    /** 路径模式（Spring Ant pattern，如 /api/admin/order/{orderNo}） */
    private String pathPattern;

    /** 模块分组（客户经营/产品与规则/运营支撑/系统管理/公共） */
    private String moduleGroup;

    /** 可用端（WEB/MINI_APP，逗号分隔） */
    private String clientTypes;

    /** 状态（ACTIVE/DISABLED） */
    private String status;

    /** 备注（接口用途） */
    private String remark;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
