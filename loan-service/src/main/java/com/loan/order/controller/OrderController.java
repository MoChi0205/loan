package com.loan.order.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.order.dto.OrderCreateReq;
import com.loan.order.dto.OrderStatusReq;
import com.loan.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务工单 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 新建工单（谁建单归谁）。
     *
     * @param req  请求（clientCode / customerGroup 必填）
     * @param user 当前用户
     * @return 工单号（业务唯一ID）
     */
    @PostMapping
    @OpLog(bizType = "服务工单", action = "CREATE")
    public Result<String> create(@RequestBody OrderCreateReq req, @CurrentUser LoanUser user) {
        return Result.ok(orderService.create(req, user == null ? null : user.getUserNo(),
                user == null ? "system" : user.getName()));
    }

    /**
     * 工单分页（老板/主管看全部，顾问只看自己的）。
     *
     * @param status   状态（可选）
     * @param keyword  工单号 / 客户编码关键字（可选）
     * @param mineOnly 仅我的工单（可选）
     * @param page     页码
     * @param size     每页大小
     * @param user     当前用户
     * @return 工单分页
     */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @CurrentUser LoanUser user) {
        return Result.ok(orderService.page(status, keyword, mineOnly,
                user == null ? null : user.getRoleCode(),
                user == null ? null : user.getUserNo(),
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100),
                orderBy, orderDir));
    }

    /**
     * 工单详情。
     *
     * @param orderNo 工单号（业务唯一ID）
     * @return 工单详情
     */
    @GetMapping("/{orderNo}")
    public Result<Map<String, Object>> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    /**
     * 工单状态流转。
     *
     * @param orderNo 工单号（业务唯一ID）
     * @param req     目标状态（+ 成交金额/时间）
     * @param user    当前用户
     * @return 成功标记
     */
    @PutMapping("/{orderNo}/status")
    @OpLog(bizType = "服务工单", action = "STATUS")
    public Result<Void> updateStatus(@PathVariable String orderNo, @RequestBody OrderStatusReq req,
                                     @CurrentUser LoanUser user) {
        orderService.updateStatus(orderNo, req, user == null ? "system" : user.getName());
        return Result.ok();
    }
}
