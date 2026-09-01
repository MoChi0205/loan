package com.loan.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.product.entity.BankProductCity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 产品-服务城市关系 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface BankProductCityMapper extends BaseMapper<BankProductCity> {

    /**
     * 批量幂等写入产品城市关系。
     *
     * @param items 待写入关系
     * @return 实际新增条数
     */
    @Insert({"<script>",
            "INSERT IGNORE INTO t_bank_product_city",
            "(product_city_code, product_code, province, city, created_by, created_at) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.productCityCode}, #{item.productCode}, #{item.province}, #{item.city},",
            " #{item.createdBy}, #{item.createdAt})",
            "</foreach>",
            "</script>"})
    int insertIgnoreBatch(@Param("items") List<BankProductCity> items);
}
