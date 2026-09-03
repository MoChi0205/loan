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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
                    customerGroup, status, productName, null, null, "channel-self", channelCode, p, s));
        }
        return Result.ok(productQueryService.queryProductsForAdmin(customerGroup, status, productName, bankName, city, scope, null, p, s));
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
        return Result.ok(productService.create(req, user == null ? "system" : user.getName()));
    }

    /**
     * 编辑产品。
     *
     * @param req  请求（含 productCode）
     * @param user 当前用户
     * @return 成功标记
     */
    @PutMapping
    @OpLog(bizType = "产品", action = "UPDATE")
    public Result<Void> update(@RequestBody ProductSaveReq req, @CurrentUser LoanUser user) {
        productService.update(req, user == null ? "system" : user.getName());
        return Result.ok();
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
     * 删除产品。
     *
     * @param productCode 产品编码（业务唯一ID）
     * @param user        当前用户
     * @return 成功标记
     */
    @DeleteMapping("/{productCode}")
    @OpLog(bizType = "产品", action = "DELETE")
    public Result<Void> delete(@PathVariable String productCode, @CurrentUser LoanUser user) {
        productService.deleteByCode(productCode, user == null ? "system" : user.getName());
        return Result.ok();
    }
}
