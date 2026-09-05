package com.loan.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.submission.entity.ClientBusinessFact;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户经营事实 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientBusinessFactMapper extends BaseMapper<ClientBusinessFact> {
}
