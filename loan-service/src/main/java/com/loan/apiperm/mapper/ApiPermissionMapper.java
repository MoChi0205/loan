package com.loan.apiperm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.apiperm.entity.ApiPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口权限定义 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ApiPermissionMapper extends BaseMapper<ApiPermission> {
}
