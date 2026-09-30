package com.loan.resourcepool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源池规则配置。资源池是控制域，不重复存客户；客户仍以 t_client_profile 的归属/公海字段为真源。
 */
@Data
@TableName("t_resource_pool_config")
public class ResourcePoolConfig implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String poolCode;
    private String poolName;
    /** COMPANY / TEAM / STAFF。 */
    private String scopeType;
    /** TEAM=部门编码，STAFF=员工工号，COMPANY=NULL。 */
    private String scopeKey;
    private Integer enabled;
    private Integer recycleEnabled;
    private Integer recycleDays;
    private Integer warnDays;
    private Integer cooldownDays;
    /** 跟进状态到无活跃天数的 JSON 映射。 */
    private String statusDaysJson;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
