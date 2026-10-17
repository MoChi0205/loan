package com.loan.staff.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.staff.entity.Staff;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
    /** 事务内按业务编码锁定员工；FOR UPDATE 仅为锁语义，不拼接业务条件。 */
    default Long lockByStaffCode(String staffCode) {
        Staff staff = selectOne(new LambdaQueryWrapper<Staff>().select(Staff::getId).eq(Staff::getStaffCode, staffCode)
            .eq(Staff::getStatus, "ACTIVE").last("FOR UPDATE"));
        return staff == null ? null : staff.getId();
    }
}
