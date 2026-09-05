package com.loan.blacklist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.blacklist.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 黑名单 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface BlacklistMapper extends BaseMapper<Blacklist> {
}
