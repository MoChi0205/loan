package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序端：服务单 / 工单（C7 四维筛选）。
 *
 * <p>权限由后端强校验，不依赖前端传参：
 * <ul>
 *   <li>客户：仅返回本人服务单，并<b>忽略</b> clientName / phone 等跨用户检索参数，
 *       仅 status 与 dateRange 生效。</li>
 *   <li>企业员工（顾问/经理/老板/运营/超管）：可查全量，支持四维组合。</li>
 *   <li>渠道：沙箱隔离，返回空列表。</li>
 * </ul>
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/order")
@RequiredArgsConstructor
public class MiniOrderController {

    private final MiniOrderService miniOrderService;

    /**
     * 服务单 / 工单列表（C7 四维筛选）。
     *
     * @param status     工单状态：PENDING / PROCESSING / SUPPLEMENT / DONE / CANCELED
     * @param clientName 客户姓名（模糊，仅员工生效）
     * @param phone      手机号（精确，仅员工生效，后端做摘要比对）
     * @param dateRange  日期区间：today / 7d / 30d / all
     * @param page       页码
     * @param size       每页大小
     * @param user       当前用户
     * @return 工单分页
     */
    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String dateRange,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        requireLogin(user);
        int p = page <= 0 ? 1 : page;
        int s = size <= 0 ? 10 : size;

        // 渠道：沙箱隔离，无服务单场景
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            return Result.ok(PageResult.build(p, s, 0L, new java.util.ArrayList<Map<String, Object>>()));
        }

        // 客户：强制只看自己的，忽略跨用户检索参数
        if (LoanUser.TYPE_CUSTOMER.equals(user.getUserType())) {
            return Result.ok(miniOrderService.myOrdersByFilter(user.getUserNo(), status, dateRange, p, s));
        }

        // 企业员工：全量 + 四维组合
        return Result.ok(miniOrderService.allOrders(status, clientName, phone, dateRange, p, s));
    }

    /**
     * 服务单详情（C7 角色二分：仅客户校验归属；员工全量可看；渠道沙箱隔离）。
     *
     * @param orderNo 工单号
     * @param user    当前用户
     * @return 工单详情
     */
    @GetMapping("/{orderNo}")
    public Result<Map<String, Object>> detail(@PathVariable String orderNo, @CurrentUser LoanUser user) {
        requireLogin(user);
        // 渠道：沙箱隔离，不可见客户服务单
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可见客户服务单");
        }
        // 客户：校验归属；企业员工：全量可看（传 null 跳过归属校验）
        String clientCode = LoanUser.TYPE_CUSTOMER.equals(user.getUserType()) ? user.getUserNo() : null;
        return Result.ok(miniOrderService.myOrderDetail(orderNo, clientCode));
    }

    /**
     * 取当前客户编码（仅客户可用）。
     *
     * @param user 当前用户
     * @return 客户编码
     */
    private String requireClient(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user.getUserNo();
    }

    /**
     * 登录校验（客户 / 渠道 / 员工均可，具体权限由各接口自行判定）。
     *
     * @param user 当前用户
     */
    private void requireLogin(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
    }
}
