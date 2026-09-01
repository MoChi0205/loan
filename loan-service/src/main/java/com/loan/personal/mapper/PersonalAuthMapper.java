package com.loan.personal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.personal.entity.PersonalAuth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人认证记录 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface PersonalAuthMapper extends BaseMapper<PersonalAuth> {
}
