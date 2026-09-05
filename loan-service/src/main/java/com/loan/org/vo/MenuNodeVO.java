package com.loan.org.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单/部门树节点 VO。
 *
 * @author loan-platform
 */
@Data
public class MenuNodeVO {

    /** 节点 ID */
    private Long id;

    /** 上级节点 ID（菜单树用） */
    private Long parentId;

    /** 上级编码（部门树用：上级部门 dept_code） */
    private String parentCode;

    /** 名称 */
    private String name;

    /** 编码（菜单 path / 部门 dept_code） */
    private String code;

    /** 前端组件路径（仅菜单叶子） */
    private String component;

    /** 类型（MENU/BUTTON） */
    private String type;

    /** 操作权限码 */
    private String permissionCode;

    /** 客群维度（T10/D28：ENTERPRISE / PERSONAL / COMMON，透传菜单授权维度） */
    private String customerGroup;

    /** 排序 */
    private Integer sort;

    /** 子节点 */
    private List<MenuNodeVO> children = new ArrayList<>();
}
