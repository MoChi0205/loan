package com.loan.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.partner.entity.PartnerProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合作库上架 Mapper（t_partner_product）。
 *
 * @author loan-platform
 */
@Mapper
public interface PartnerProductMapper extends BaseMapper<PartnerProduct> {
}
