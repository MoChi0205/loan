package com.loan.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 银行产品-服务城市关系实体（t_bank_product_city）。
 *
 * <p>产品可服务多个城市（市一级，省市名称字符串），无物理外键，以 product_code 业务唯一键关联。
 * 企业申请时填写申请城市，匹配时按申请城市精确筛选产品。
 *
 * @author loan-platform
 */
@Data
@TableName("t_bank_product_city")
public class BankProductCity {

    /** 物理主键（自增，仅内部使用，不作业务关联） */
    @JsonIgnore
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品城市关系业务编码（pcity + 小写字母数字，总长 16） */
    private String productCityCode;

    /** 产品业务唯一键（t_bank_product.product_code，无物理外键） */
    private String productCode;

    /** 省（名称字符串，如 湖北省） */
    private String province;

    /** 市（名称字符串，市一级，如 武汉市） */
    private String city;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
