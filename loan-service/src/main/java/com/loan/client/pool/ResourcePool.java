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
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
