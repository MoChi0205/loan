package com.loan.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.submission.entity.ClientSubmission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户资料提交 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientSubmissionMapper extends BaseMapper<ClientSubmission> {
}
