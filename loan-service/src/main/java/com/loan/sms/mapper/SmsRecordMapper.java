package com.loan.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.sms.entity.SmsRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信记录 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface SmsRecordMapper extends BaseMapper<SmsRecord> {
}
