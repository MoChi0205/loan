package com.loan.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.product.entity.BankProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 银行产品 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface BankProductMapper extends BaseMapper<BankProduct> {
}
