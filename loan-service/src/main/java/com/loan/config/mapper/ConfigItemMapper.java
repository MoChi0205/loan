package com.loan.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.config.entity.ConfigItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置项 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ConfigItemMapper extends BaseMapper<ConfigItem> {
}
