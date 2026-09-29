package com.loan.serviceops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.serviceops.entity.StaffOuting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工外出 Mapper。
 *
 * <p>所有状态流转都用「条件更新」实现乐观并发控制：WHERE 里同时带上「期望的当前状态」，
 * 更新行数不为 1 即说明状态已被他人改变，由服务层抛出可重试的业务提示。
 */
@Mapper
public interface StaffOutingMapper extends BaseMapper<StaffOuting> {
}
