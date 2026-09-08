package com.loan.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.report.entity.ScreeningProduct;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报告命中产品明细 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ScreeningProductMapper extends BaseMapper<ScreeningProduct> {

    /**
     * 批量落报告命中产品明细（一次多值 INSERT，消除循环单条 insert 的 N+1）。
     * 主键由数据库自增生成；调用前需保证 list 非空。
     *
     * @param list 产品明细列表
     * @return 受影响行数
     */
    @Insert({"<script>INSERT INTO t_screening_product (report_no, product_code, hit_result, match_score, created_at) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.reportNo}, #{item.productCode}, #{item.hitResult}, #{item.matchScore}, #{item.createdAt})",
            "</foreach></script>"})
    int insertBatch(@Param("list") List<ScreeningProduct> list);
}
