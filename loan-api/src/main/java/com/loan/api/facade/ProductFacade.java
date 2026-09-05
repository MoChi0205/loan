package com.loan.api.facade;

import com.loan.api.dto.PageResult;
import com.loan.api.dto.Result;
import com.loan.api.dto.product.ProductDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 产品库服务契约（Dubbo 跨系统接口）。
 *
 * @author loan-platform
 */
@DubboService(version = "1.0.0")
public interface ProductFacade {

    /**
     * 分页查询全量库产品。
     *
     * @param customerGroup 客群（可选，为空查全部）
     * @param page          页码（从 1 起）
     * @param size          每页大小
     * @return 产品分页结果
     */
    Result<PageResult<ProductDTO>> queryProducts(String customerGroup, int page, int size);
}
