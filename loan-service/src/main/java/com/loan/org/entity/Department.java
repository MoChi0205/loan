package com.loan.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 部门实体（t_department，通用部门树）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_department")
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部门编码 */
    private String deptCode;

    /** 部门名称 */
    private String deptName;

    /** 上级部门编码（业务编码，顶级为 NULL） */
    private String parentCode;

    /** 负责人员员工号（业务编码） */
    private String leaderStaffCode;

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
