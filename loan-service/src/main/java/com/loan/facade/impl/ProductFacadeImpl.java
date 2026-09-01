package com.loan.facade.impl;

import com.loan.api.dto.PageResult;
import com.loan.api.dto.Result;
import com.loan.api.dto.product.ProductDTO;
import com.loan.api.facade.ProductFacade;
import com.loan.product.service.ProductQueryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 产品库服务实现（开发阶段 {@code @Service}；跨系统 Dubbo 暴露时改 {@code @DubboService}）。
 *
 * @author loan-platform
 */
@Service
public class ProductFacadeImpl implements ProductFacade {

    @Resource
    private ProductQueryService productQueryService;

    @Override
    public Result<PageResult<ProductDTO>> queryProducts(String customerGroup, int page, int size) {
        try {
            int p = page <= 0 ? 1 : page;
            int s = size <= 0 ? 10 : Math.min(size, 100);
            return Result.ok(productQueryService.queryProducts(customerGroup, p, s));
        } catch (Exception e) {
            return Result.fail(4000, "产品查询失败: " + e.getMessage());
        }
    }
}
