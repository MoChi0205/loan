package com.loan.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.attachment.entity.ServiceAttachment;
import com.loan.attachment.mapper.ServiceAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 服务资料附件服务：分页查询。
 *
 * <p>查看/下载统一动态水印（原文件永不出库，走服务端受控接口）；
 * 上传（本地磁盘模式 / 阿里云 OSS，复用 tse OssStorageService）后续迭代接入。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final ServiceAttachmentMapper serviceAttachmentMapper;

    /**
     * 分页查询附件（按客户编码 / 工单号）。
     *
     * @param clientProfileCode 客户编码（可选，业务唯一ID）
     * @param orderNo           工单号（可选，业务唯一ID）
     * @param page              页码
     * @param size              每页大小
     * @return 附件分页
     */
    public PageResult<ServiceAttachment> page(String clientProfileCode, String orderNo, String keyword, int page, int size) {
        LambdaQueryWrapper<ServiceAttachment> wrapper = new LambdaQueryWrapper<>();
        if (clientProfileCode != null && !clientProfileCode.isEmpty()) {
            wrapper.eq(ServiceAttachment::getClientProfileCode, clientProfileCode);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.eq(ServiceAttachment::getOrderNo, orderNo);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ServiceAttachment::getFileName, kw)
                    .or().like(ServiceAttachment::getAttachmentType, kw)
                    .or().like(ServiceAttachment::getOrderNo, kw));
        }
        wrapper.orderByDesc(ServiceAttachment::getUploadTime);
        Page<ServiceAttachment> result = serviceAttachmentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }
}
