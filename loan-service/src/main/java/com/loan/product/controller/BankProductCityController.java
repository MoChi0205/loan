package com.loan.product.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.product.entity.BankProductCity;
import com.loan.product.model.ProductCityBatchRequest;
import com.loan.product.model.ProductCityQuery;
import com.loan.product.model.ProductCityUpdateRequest;
import com.loan.product.service.BankProductCityService;
import com.loan.product.service.BankProductCityService.CityItem;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品-服务城市 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/product-city")
@RequiredArgsConstructor
public class BankProductCityController {

    private final BankProductCityService cityService;

    /** 统一组合分页查询产品城市关系 */
    @GetMapping("/page")
    public Result<PageResult<BankProductCity>> page(ProductCityQuery query) {
        return Result.ok(cityService.page(query));
    }

    /** 查询产品绑定的城市 */
    @GetMapping("/{productCode}")
    public Result<List<BankProductCity>> list(@PathVariable String productCode) {
        return Result.ok(cityService.listByProduct(productCode));
    }

    /** 按业务编码查询单条产品城市关系 */
    @GetMapping("/relation/{productCityCode}")
    public Result<BankProductCity> detail(@PathVariable String productCityCode) {
        return Result.ok(cityService.detail(productCityCode));
    }

    /** 按业务编码批量查询产品城市关系 */
    @PostMapping("/batch-query")
    public Result<List<BankProductCity>> batchQuery(@RequestBody ProductCityBatchRequest request) {
        return Result.ok(cityService.batchQuery(request == null ? null : request.getProductCityCodes()));
    }

    /** 批量绑定产品可服务城市 */
    @PostMapping("/{productCode}")
    public Result<Integer> bind(@PathVariable String productCode, @RequestBody List<CityItem> cities,
                                @CurrentUser LoanUser user) {
        return Result.ok(cityService.bind(productCode, cities, operatorName(user)));
    }

    /** 按业务编码修改产品城市关系，业务编码自身不可修改 */
    @PutMapping("/relation/{productCityCode}")
    public Result<String> update(@PathVariable String productCityCode,
                                 @RequestBody ProductCityUpdateRequest request) {
        cityService.update(productCityCode, request);
        return Result.ok("ok");
    }

    /** 解绑单条城市 */
    @DeleteMapping("/relation/{productCityCode}")
    public Result<String> unbind(@PathVariable String productCityCode) {
        cityService.unbind(productCityCode);
        return Result.ok("ok");
    }

    private String operatorName(LoanUser user) {
        return user == null ? "system" : (user.getName() == null ? "system" : user.getName());
    }
}
