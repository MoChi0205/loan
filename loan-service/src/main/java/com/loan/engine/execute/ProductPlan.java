package com.loan.engine.execute;

import lombok.Getter;

/**
 * 产品与准入计划的绑定（对应 t_product_admission_config：产品 ↔ 执行计划）。
 *
 * <p>调试中心影子执行时由示例提供器构造；正式匹配由计划加载服务从 DB 组装。
 *
 * @author loan-platform
 */
@Getter
public class ProductPlan {

    /** 产品 ID */
    private final Long productId;

    /** 产品编码 */
    private final String productCode;

    /** 产品名称（仅管理端可见） */
    private final String productName;

    /** 准入执行计划 */
    private final AdmissionPlan plan;

    /**
     * 构造产品-计划绑定。
     *
     * @param productId   产品 ID
     * @param productCode 产品编码
     * @param productName 产品名称
     * @param plan        准入计划
     */
    public ProductPlan(Long productId, String productCode, String productName, AdmissionPlan plan) {
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.plan = plan;
    }
}
