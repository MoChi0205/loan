package com.loan.partner.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.log.annotation.OpLog;
import com.loan.partner.dto.PartnerProductSaveReq;
import com.loan.partner.entity.PartnerProduct;
import com.loan.partner.service.PartnerProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 合作库接口（P0-5）：
 * <ul>
 *   <li>管理端：/api/admin/partner-product/page、POST、/{code}/renew、/{code}/status（仅 STAFF）</li>
 *   <li>小程序只读：/api/mini/partner-product/active（仅 ACTIVE 未过期，登录后可访问）</li>
 * </ul>
 *
 * @author loan-platform
 */
@RestController
@RequiredArgsConstructor
public class PartnerProductController {

    private final PartnerProductService partnerProductService;

    /**
     * 管理端分页查询。
     *
     * @param status 状态（可选）
     * @param page   页码
     * @param size   每页大小
     * @param user   当前用户
     * @return 分页结果
     */
    @GetMapping("/api/admin/partner-product/page")
    public Result<PageResult<PartnerProduct>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        requireStaff(user);
        int p = PageParams.page(page);
        int s = PageParams.size(size);
        return Result.ok(partnerProductService.page(status, p, s));
    }

    /**
     * 管理端新增上架。
     *
     * @param req  请求（bankProductCode + cooperateUntil）
     * @param user 当前用户
     * @return 银行产品业务编码
     */
    @PostMapping("/api/admin/partner-product")
    @OpLog(bizType = "合作库", action = "CREATE")
    public Result<String> create(@RequestBody PartnerProductSaveReq req, @CurrentUser LoanUser user) {
        requireStaff(user);
        return Result.ok(partnerProductService.create(req, user == null ? "system" : user.getName()));
    }

    /**
     * 管理端续签（更新有效期，状态回到 ACTIVE）。
     *
     * @param code 银行产品业务编码
     * @param body {cooperateUntil: "2026-12-31T00:00:00"}
     * @param user 当前用户
     * @return 成功标记
     */
    @PutMapping("/api/admin/partner-product/{code}/renew")
    @OpLog(bizType = "合作库", action = "RENEW")
    public Result<Void> renew(@PathVariable String code, @RequestBody Map<String, String> body,
                              @CurrentUser LoanUser user) {
        requireStaff(user);
        String until = body == null ? null : body.get("cooperateUntil");
        if (!StringUtils.hasText(until)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "cooperateUntil 必填");
        }
        partnerProductService.renew(code, LocalDateTime.parse(until),
                user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 管理端状态流转（手动下架 OFFLINE / 上架 ACTIVE）。
     *
     * @param code 银行产品业务编码
     * @param body {status: "OFFLINE"}
     * @param user 当前用户
     * @return 成功标记
     */
    @PutMapping("/api/admin/partner-product/{code}/status")
    @OpLog(bizType = "合作库", action = "STATUS")
    public Result<Void> updateStatus(@PathVariable String code, @RequestBody Map<String, String> body,
                                     @CurrentUser LoanUser user) {
        requireStaff(user);
        String status = body == null ? null : body.get("status");
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "status 必填");
        }
        partnerProductService.updateStatus(code, status, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 小程序只读：ACTIVE 未过期合作产品列表。
     *
     * @param user 当前客户
     * @return 合作产品列表
     */
    @GetMapping("/api/mini/partner-product/active")
    public Result<List<PartnerProduct>> active(@CurrentUser LoanUser user) {
        if (user == null || !StringUtils.hasText(user.getUserNo())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.ok(partnerProductService.listActive());
    }

    /**
     * 校验管理端操作权限（仅 STAFF）。
     *
     * @param user 当前用户
     */
    private void requireStaff(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅员工可操作合作库");
        }
    }
}
