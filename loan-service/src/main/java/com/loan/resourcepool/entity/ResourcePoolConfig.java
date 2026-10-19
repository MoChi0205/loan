package com.loan.resourcepool.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源池回收规则视图（DTO）。
 *
 * <p>2026-10-19 起规则内嵌到 {@code t_resource_pool}（每池一条，参考 tse），本类不再是独立表实体，
 * 仅作为 {@code ResourcePoolConfigService} 对外暴露规则视图的承载对象；
 * scopeType/scopeKey 由池类型映射（TEAM→部门编码 / COMPANY→NULL）。</p>
 */
@Data
public class ResourcePoolConfig implements Serializable {
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
