package com.loan.product.controller;

import com.loan.api.dto.PageResult;
import com.loan.api.dto.product.ProductDTO;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.product.dto.ProductSaveReq;
import com.loan.product.entity.BankProduct;
import com.loan.product.service.ProductQueryService;
import com.loan.product.service.ProductService;
import com.loan.common.util.PageParams;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 产品库 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/product")
public class ProductController {

    @Resource
    private ProductQueryService productQueryService;

    @Resource
    private ProductService productService;

    /**
     * 管理端分页查询（不过滤 APPROVED，支持状态 / 名称 / 银行筛选）。
     *
     * @param customerGroup 客群（可选）
     * @param status        状态（可选）
     * @param productName   产品名称（可选，模糊）
     * @param bankName      所属银行（可选，模糊）
     * @param scope         库类型（all/cooperate；渠道忽略此参数，强制本行）
     * @param page          页码（从 1 起）
     * @param size          每页大小
     * @param user          当前用户（渠道强制本行硬隔离，T11/D28）
     * @return 产品分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ProductDTO>> page(
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAtStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAtEnd,
            @CurrentUser LoanUser user) {
        int p = PageParams.page(page);
        int s = PageParams.size(size);
        // 渠道沙箱：数据范围硬隔离——仅本银行渠道产品；无渠道编码时返回空集（防越权看全量）
        if (user != null && LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            String channelCode = user.getBankChannelCode();
            if (channelCode == null || channelCode.isEmpty()) {
                return Result.ok(PageResult.build(p, s, 0, java.util.Collections.emptyList()));
            }
            return Result.ok(productQueryService.queryProductsForAdmin(
                    customerGroup, status, productName, null, null, "channel-self", channelCode, null, p, s, createdAtStart, createdAtEnd));
        }
        boolean manager = isProductManager(user);
        // 全量库可见角色：老板/超管/运营看全量；其余员工仅可见本人创建的产品
        boolean fullScope = manager || isOperator(user);
        if ("cooperate".equalsIgnoreCase(scope) && !manager) {
            throw new BusinessException(ResultCode.FORBIDDEN, "合作库仅老板或超级管理员可访问");
        }
        String visibleStatus = fullScope ? status : null;
        String staffNo = fullScope ? null : (user == null ? null : user.getUserNo());
        return Result.ok(productQueryService.queryProductsForAdmin(customerGroup, visibleStatus, productName, bankName, city, scope, null, staffNo, p, s, createdAtStart, createdAtEnd));
    }

    /**
     * 新增产品。
     *
     * @param req  请求
     * @param user 当前用户
     * @return 产品编码（业务唯一ID）
     */
    @PostMapping
    @OpLog(bizType = "产品", action = "CREATE")
    public Result<String> create(@RequestBody ProductSaveReq req, @CurrentUser LoanUser user) {
        return Result.ok(productService.create(req,
                user == null ? "system" : user.getName(),
                user == null ? null : user.getUserNo()));
    }

    /**
     * 编辑产品：老板/超管直接生效；其余员工仅可对本人创建的产品提交变更审批。
     *
     * @param req  请求（含 productCode）
     * @param user 当前用户
     * @return 直接生效返回空串；走审批时返回审批工单号
     */
    @PutMapping
    @OpLog(bizType = "产品", action = "UPDATE")
    public Result<String> update(@RequestBody ProductSaveReq req, @CurrentUser LoanUser user) {
        if (isProductManager(user)) {
            productService.update(req, user == null ? "system" : user.getName());
            return Result.ok("");
        }
        String approvalNo = productService.submitUpdateApproval(req,
                user == null ? "system" : user.getName(),
                user == null ? null : user.getUserNo());
        return Result.ok(approvalNo);
    }

    /**
     * 产品详情。
     *
     * @param productCode 产品编码（业务唯一ID）
     * @return 产品实体
     */
    @GetMapping("/{productCode}")
    public Result<BankProduct> get(@PathVariable String productCode) {
        return Result.ok(productService.getByCode(productCode));
    }

    /**
     * 删除产品：老板/超管直接逻辑下线；其余员工提交下线审批（删除理由必填，审批通过后 OFFLINE）。
     *
     * @param productCode 产品编码（业务唯一ID）
     * @param reason      删除理由（员工提交审批时必填）
     * @param user        当前用户
     * @return 直接生效返回空串；走审批时返回审批工单号
     */
    @DeleteMapping("/{productCode}")
    @OpLog(bizType = "产品", action = "DELETE")
    public Result<String> delete(@PathVariable String productCode,
                                 @RequestParam(required = false) String reason,
                                 @CurrentUser LoanUser user) {
        if (isProductManager(user)) {
            productService.deleteByCode(productCode, user == null ? "system" : user.getName());
            return Result.ok("");
        }
        String approvalNo = productService.submitOfflineApproval(productCode, reason,
                user == null ? "system" : user.getName(),
                user == null ? null : user.getUserNo());
        return Result.ok(approvalNo);
    }

    private boolean isProductManager(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())) return false;
        String role = String.valueOf(user.getRoleCode()).toUpperCase();
        return "BOSS".equals(role) || "SUPER_ADMIN".equals(role) || "SUPER".equals(role);
    }

    /** 运营：可查看全量产品库（但新增/变更/下线仍按需审批，不能直接操作已发布产品） */
    private boolean isOperator(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())) return false;
        return "OPERATOR".equals(String.valueOf(user.getRoleCode()).toUpperCase());
    }
}
