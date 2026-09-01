package com.loan.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.lead.entity.Lead;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface LeadMapper extends BaseMapper<Lead> {
}
