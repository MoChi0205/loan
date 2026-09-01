package com.loan.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单实体（t_menu，菜单树 + 操作按钮清单）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_menu")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级菜单 ID（顶级为 NULL） */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 前端路由路径（禁止重复） */
    private String path;

    /** 前端组件路径（叶子才挂 component） */
    private String component;

    /** 类型（MENU 菜单 / BUTTON 操作按钮） */
    private String menuType;

    /** 操作权限码（如 client:add / lead:claim） */
    private String permissionCode;

    /** 客群维度（T10/D28：ENTERPRISE 企业 / PERSONAL 个人 / COMMON 通用，供按客户群分别授权） */
    private String customerGroup;

    /** 排序 */
    private Integer sort;

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
