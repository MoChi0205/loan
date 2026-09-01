package com.loan.product.model;

import lombok.Data;

import java.util.List;

/**
 * 产品城市关系批量查询请求。
 *
 * @author loan-platform
 */
@Data
public class ProductCityBatchRequest {

    /** 产品城市关系业务编码集合 */
    private List<String> productCityCodes;
}
