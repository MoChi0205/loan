package com.loan.attachment.controller;

import com.loan.api.dto.PageResult;
import com.loan.attachment.entity.ServiceAttachment;
import com.loan.attachment.service.AttachmentService;
import com.loan.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务资料附件 HTTP 接口。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * 分页查询附件。
     *
     * @param clientProfileCode 客户编码（可选，业务唯一ID）
     * @param orderNo           工单号（可选，业务唯一ID）
     * @param page              页码
     * @param size              每页大小
     * @return 附件分页
     */
    @GetMapping("/page")
    public Result<PageResult<ServiceAttachment>> page(
            @RequestParam(required = false) String clientProfileCode,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(attachmentService.page(clientProfileCode, orderNo, page, size));
    }
}
