package com.loan.sensitive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.sensitive.entity.SensitiveViewGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感数据查看授权 Mapper（受限角色申请后授权，UK 防并发重复）。
 *
 * @author loan-platform
 */
@Mapper
public interface SensitiveViewGrantMapper extends BaseMapper<SensitiveViewGrant> {
}
