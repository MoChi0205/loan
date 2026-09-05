package com.loan.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.sms.entity.SmsTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信模板 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface SmsTemplateMapper extends BaseMapper<SmsTemplate> {
}
