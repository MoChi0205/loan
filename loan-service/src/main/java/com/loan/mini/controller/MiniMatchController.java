package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.dto.MiniMatchResult;
import com.loan.mini.service.MiniMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 小程序端：匹配执行 / 匹配历史 / 报告列表与详情 / 命中产品 / 经营诊断。
 *
 * <p>报告查询与产品可见性遵循「模块结论」C3（角色二分）、C4（命中产品）、
 * C5（经营诊断）、C11（四维查询）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini")
@RequiredArgsConstructor
public class MiniMatchController {

    private final MiniMatchService miniMatchService;

    /**
     * 发起匹配（客户提交经营事实 → 引擎匹配 → 生成报告）。
     *
     * <p>入参 {facts:{...}, applyCity?, clientSubmitId?, clientCode?}；返回对客脱敏结果
     * （仅报告号/档位/三档总结果/产品数量/评级/规则说明，不含产品明细）。
     *
     * <p><b>归属对象（C2 替客匹配）：</b>
     * <ul>
     *   <li>客户 CUSTOMER：强制以登录态 clientCode 为归属，<b>忽略</b> body.clientCode（防越权替客）；</li>
     *   <li>企业员工 STAFF：body.clientCode 为目标客户（步骤 0 已锁定归属），必传；</li>
     *   <li>渠道 CHANNEL：禁入（C1 渠道不可操作智能匹配）。</li>
     * </ul>
     *
     * @param body 匹配请求
     * @param user 当前用户
     * @return 脱敏匹配结果
     */
    @PostMapping("/match/run")
    public Result<MiniMatchResult> run(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        requireLogin(user);
        // 渠道禁入（C1）
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可发起匹配");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) body.get("facts");
        String applyCity = body.get("applyCity") == null ? null : String.valueOf(body.get("applyCity"));
        String clientSubmitId = body.get("clientSubmitId") == null ? null : String.valueOf(body.get("clientSubmitId"));
        String clientCode;
        if (LoanUser.TYPE_CUSTOMER.equals(user.getUserType())) {
            // 客户只能匹配自己：强制登录态，忽略前端传入 clientCode
            clientCode = user.getUserNo();
        } else {
            // 企业员工替客匹配：body 必传目标客户编码
            clientCode = body.get("clientCode") == null ? null : String.valueOf(body.get("clientCode"));
            if (!StringUtils.hasText(clientCode)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "请先选择目标客户");
            }
        }
        return Result.ok(miniMatchService.runForMini(clientCode, facts,
                user == null ? "客户" : user.getName(), applyCity, clientSubmitId));
    }

    /**
     * 我的匹配历史。
     *
     * @param page 页码
     * @param size 每页大小
     * @param user 当前客户
     * @return 匹配历史分页
     */
    @GetMapping("/match/history")
    public Result<PageResult<Map<String, Object>>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        String clientCode = requireClient(user);
        return Result.ok(miniMatchService.myMatches(clientCode, page <= 0 ? 1 : page, size <= 0 ? 10 : size));
    }

    /**
     * 报告列表（C3 角色二分 + C11 四维查询）。
     *
     * <p><b>权限强校验（不依赖前端传参，后端兜底）：</b>
     * <ul>
     *   <li>客户 CUSTOMER：只返回本人报告，并<b>忽略</b> query/credit/owner 等跨用户检索参数
     *       ——客户无权检索他人报告，仅允许 dateRange 生效。</li>
     *   <li>企业员工 STAFF（顾问/经理/老板/运营/超管）：可查全量，支持
     *       手机号/客户姓名、公司名称/统一社会信用代码、归属、日期区间四维组合（AND）。</li>
     *   <li>渠道 CHANNEL：沙箱隔离，返回空列表。</li>
     * </ul>
     *
     * @param page      页码
     * @param size      每页大小
     * @param query     手机号或客户姓名
     * @param credit    公司名称或统一社会信用代码
     * @param owner     归属：me / staff / all
     * @param dateRange 日期区间：today / 7d / 30d / all
     * @param user      当前用户
     * @return 报告分页
     */
    @GetMapping("/report/list")
    public Result<PageResult<Map<String, Object>>> reportList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String credit,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String dateRange,
            @CurrentUser LoanUser user) {
        requireLogin(user);
        int p = page <= 0 ? 1 : page;
        int s = size <= 0 ? 10 : size;

        // 渠道：沙箱隔离，不可见客户匹配报告
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            return Result.ok(PageResult.build(p, s, 0L, new ArrayList<Map<String, Object>>()));
        }

        // 客户：强制只看自己的，忽略所有跨用户检索参数
        if (LoanUser.TYPE_CUSTOMER.equals(user.getUserType())) {
            return Result.ok(miniMatchService.myReportsByDate(user.getUserNo(), p, s, dateRange));
        }

        // 企业员工：全量 + 四维组合
        return Result.ok(miniMatchService.allReports(p, s, query, credit, owner, dateRange, user.getUserNo()));
    }

    /**
     * 报告命中的银行产品（C4）。
     *
     * <p>企业员工（渠道除外）可查看某企业的匹配命中产品，用于陪访解读。
     * 渠道受沙箱隔离直接拒绝；客户不可见产品明细（对客脱敏，仅展示数量）。
     *
     * @param reportNo 报告编号
     * @param user     当前用户
     * @return 命中产品列表
     */
    @GetMapping("/report/{reportNo}/products")
    public Result<List<Map<String, Object>>> reportProducts(@PathVariable String reportNo,
                                                            @CurrentUser LoanUser user) {
        requireLogin(user);
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可见客户匹配结果");
        }
        if (LoanUser.TYPE_CUSTOMER.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "对客报告仅展示产品数量，明细请咨询您的顾问");
        }
        return Result.ok(miniMatchService.reportProducts(reportNo));
    }

    /**
     * 企业经营诊断（C5，基于报告已上传材料生成）。
     *
     * <p>材料非最新时前端提示上传最新材料，上传后调用本接口重新生成。
     * 客户可见自己的诊断；企业员工可见全量；渠道不可见。
     *
     * @param reportNo 报告编号
     * @param user     当前用户
     * @return 诊断结果（KPI / 经营建议 / 风险提示 / 历年数据 / 多维统计）
     */
    @GetMapping("/report/{reportNo}/diagnosis")
    public Result<Map<String, Object>> reportDiagnosis(@PathVariable String reportNo,
                                                       @CurrentUser LoanUser user) {
        requireLogin(user);
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可见客户经营诊断");
        }
        String clientCode = LoanUser.TYPE_CUSTOMER.equals(user.getUserType()) ? user.getUserNo() : null;
        return Result.ok(miniMatchService.reportDiagnosis(reportNo, clientCode));
    }

    /**
     * 报告详情（C3 角色二分：仅客户校验归属；员工全量可看；渠道沙箱隔离）。
     *
     * @param reportNo 报告编号
     * @param user     当前用户
     * @return 报告详情
     */
    @GetMapping("/report/{reportNo}")
    public Result<Map<String, Object>> reportDetail(@PathVariable String reportNo, @CurrentUser LoanUser user) {
        requireLogin(user);
        // 渠道：沙箱隔离，不可见客户匹配报告
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可见客户匹配结果");
        }
        // 客户：校验归属；企业员工：全量可看（传 null 跳过归属校验）
        String clientCode = LoanUser.TYPE_CUSTOMER.equals(user.getUserType()) ? user.getUserNo() : null;
        return Result.ok(miniMatchService.reportDetail(reportNo, clientCode));
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
