package com.loan.product.model;

import lombok.Data;

/**
 * 产品城市关系修改请求。
 *
 * <p>关系业务编码由路径参数提供且不可修改。
 *
 * @author loan-platform
 */
@Data
public class ProductCityUpdateRequest {

    /** 产品业务编码 */
    private String productCode;

    /** 省名称 */
    private String province;

    /** 市名称 */
    private String city;
}
