package com.loan.plan.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.plan.entity.ProductStrategy;
import com.loan.plan.service.ProductStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 渠道准入策略 HTTP 接口（Web 管理端）。
 *
 * <p>策略 = 渠道 × 产品 × 客群 → 计划(1:1)；生命周期 DISABLED(草稿) / ACTIVE(上线写锁)。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/channel-strategy")
@RequiredArgsConstructor
public class ProductStrategyController {

    private final ProductStrategyService strategyService;

    /**
     * 分页查询策略。
     */
    @GetMapping("/page")
    public Result<PageResult<ProductStrategy>> page(
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String customerGroup,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(strategyService.page(channelCode, customerGroup, keyword, page, size));
    }

    /**
     * 渠道配置摘要（每渠道一行：策略数/已上线/计划数/最近更新）。
     */
    @GetMapping("/channel-summary")
    public Result<List<Map<String, Object>>> channelSummary() {
        return Result.ok(strategyService.channelSummary());
    }

    /**
     * 新建策略（草稿态）。
     */
    @PostMapping
    public Result<String> create(@RequestBody ProductStrategy strategy, @CurrentUser LoanUser user) {
        return Result.ok(strategyService.create(strategy, operatorName(user)));
    }

    /**
     * 编辑策略（写锁校验）。
     */
    @PutMapping("/{strategyCode}")
    public Result<String> update(@PathVariable String strategyCode, @RequestBody ProductStrategy strategy,
                                 @CurrentUser LoanUser user) {
        strategyService.update(strategyCode, strategy, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 删除策略（级联删除计划树）。
     */
    @DeleteMapping("/{strategyCode}")
    public Result<String> delete(@PathVariable String strategyCode) {
        strategyService.delete(strategyCode);
        return Result.ok("ok");
    }

    /**
     * 上线（先校验计划可执行）。
     */
    @PostMapping("/{strategyCode}/enable")
    public Result<String> enable(@PathVariable String strategyCode, @CurrentUser LoanUser user) {
        strategyService.enable(strategyCode, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 下线。
     */
    @PostMapping("/{strategyCode}/disable")
    public Result<String> disable(@PathVariable String strategyCode, @CurrentUser LoanUser user) {
        strategyService.disable(strategyCode, operatorName(user));
        return Result.ok("ok");
    }

    /**
     * 上线前校验。
     */
    @PostMapping("/{strategyCode}/validate-before-enable")
    public Result<List<String>> validateBeforeEnable(@PathVariable String strategyCode) {
        return Result.ok(strategyService.validateBeforeEnable(strategyCode));
    }

    /**
     * 跨渠道复制：源策略深拷贝到目标渠道。
     */
    @PostMapping("/import-from-channel")
    public Result<String> importFromChannel(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        String sourceStrategyCode = body.get("sourceStrategyCode");
        String targetChannelCode = body.get("targetChannelCode");
        String targetStrategyCode = body.get("targetStrategyCode");
        return Result.ok(strategyService.importFromChannel(
                sourceStrategyCode, targetChannelCode, targetStrategyCode, operatorName(user)));
    }

    /**
     * 模版导入到策略：把已上线的策略模版实例化为渠道准入策略（含计划树）。
     */
    @PostMapping("/import-from-template")
    public Result<String> importFromTemplate(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        String templateCode = body.get("templateCode");
        String channelCode = body.get("channelCode");
        String strategyCode = body.get("strategyCode");
        String strategyName = body.get("strategyName");
        String bankProductCode = body.get("bankProductCode");
        String customerGroup = body.get("customerGroup");
        return Result.ok(strategyService.importFromTemplate(
                templateCode, channelCode, strategyCode, strategyName,
                bankProductCode, customerGroup, operatorName(user)));
    }

    private String operatorName(LoanUser user) {
        return user == null ? "system" : (user.getName() == null ? "system" : user.getName());
    }
}
