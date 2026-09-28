package com.loan.staff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.staff.entity.Staff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 员工映射 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface StaffMapper extends BaseMapper<Staff> {

    /**
     * 锁定员工业务行，串行化该员工的敏感数据额度占用。
     *
     * <p>必须在事务内调用。锁按 staff_code 获取，不使用自增主键建立业务关联。</p>
     *
     * @param staffCode 员工业务编码
     * @return 员工物理主键，仅用于确认锁定成功，不对外暴露
     */
    @Select("SELECT id FROM t_staff WHERE staff_code = #{staffCode} LIMIT 1 FOR UPDATE")
    Long lockByStaffCode(@Param("staffCode") String staffCode);
}
