package com.loan.personal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.personal.entity.PersonalProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人客户档案 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface PersonalProfileMapper extends BaseMapper<PersonalProfile> {
}
