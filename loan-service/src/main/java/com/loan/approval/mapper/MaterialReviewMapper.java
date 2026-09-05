package com.loan.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.approval.entity.MaterialReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 材料复核审批单 Mapper（t_material_review）。
 *
 * @author loan-platform
 */
@Mapper
public interface MaterialReviewMapper extends BaseMapper<MaterialReview> {
}
