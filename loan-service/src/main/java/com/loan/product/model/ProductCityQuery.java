package com.loan.product.model;

import lombok.Data;

/**
 * 产品城市关系统一组合分页查询模型。
 *
 * @author loan-platform
 */
@Data
public class ProductCityQuery {

    /** 产品业务编码 */
    private String productCode;

    /** 省名称 */
    private String province;

    /** 市名称 */
    private String city;

    /** 省、市模糊关键词 */
    private String keyword;

    /** 页码 */
    private int page = 1;

    /** 每页大小 */
    private int size = 10;
}
