package com.loan.client.pool;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 资源池定义：导入与公海分配的目标池。 */
@Data @TableName("t_resource_pool")
public class ResourcePool {
    @TableId(type = IdType.AUTO) private Long id;
    private String poolCode;
    private String poolName;
    /** COMPANY 公司池 / TEAM 团队池 */
    private String poolType;
    /** 团队池关联部门编码（公司池为空） */
    private String deptCode;
    /** 团队池关联部门名称 */
    private String deptName;
    private String description;
    /** ACTIVE 启用 / INACTIVE 停用 */
    private String status;
    /** 自动回收开关（1=开启；参考 tse t_resource_pool.auto_recycle_enabled：规则内嵌在池上） */
    private Integer recycleEnabled;
    /** 默认回收天数（客户标签未命中按状态天数时使用） */
    private Integer recycleDays;
    /** 提前预警天数 */
    private Integer warnDays;
    /** 回收冷却天数 */
    private Integer cooldownDays;
    /** 按跟进状态的无活跃回收天数 JSON（如 {"NEW":30,"NO_ANSWER":15}） */
    private String statusDaysJson;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
