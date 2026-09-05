package com.loan.ocr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.ocr.entity.OcrRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片识别记录 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface OcrRecordMapper extends BaseMapper<OcrRecord> {
}
