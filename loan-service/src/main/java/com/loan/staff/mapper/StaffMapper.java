package com.loan.staff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.staff.entity.Staff;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工映射 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
}
