package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.serviceops.dto.ClientInsightDTO;
import com.loan.serviceops.dto.InsightReviewRequest;
import com.loan.serviceops.service.ClientInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web 员工端客户画像快照接口。
 *
 * <p>权限边界：
 * <ul>
 *   <li>读取与生成：按名单数据范围（顾问本人 / 部门经理本部门 / 全公司）；生成额外限定为当前归属顾问本人；</li>
 *   <li>复核：部门经理本部门与全公司角色，禁止自复核；</li>
 *   <li>渠道账号无任何内部名单范围，一律拒绝；客户端不走本接口，只读快照里的客户摘要。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/client")
@RequiredArgsConstructor
public class AdminClientInsightController {

    private final ClientInsightService insightService;

    /** 当前生效的客户画像快照；未生成时返回空数据由前端展示空态。 */
    @GetMapping("/{clientCode}/insight")
    public Result<ClientInsightDTO> insight(@PathVariable String clientCode,
                                            @CurrentUser LoanUser user) {
        return Result.ok(insightService.current(clientCode, user));
    }

    /** 画像版本链，用于回放画像前后变化。 */
    @GetMapping("/{clientCode}/insight/history")
    public Result<PageResult<ClientInsightDTO>> insightHistory(
            @PathVariable String clientCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        return Result.ok(insightService.history(clientCode, PageParams.page(page),
                PageParams.size(size), user));
    }

    /** 生成一版新的画像快照（仅当前归属顾问本人）。 */
    @PostMapping("/{clientCode}/insight/generate")
    @OpLog(bizType = "客户画像", action = "GENERATE")
    public Result<String> generateInsight(@PathVariable String clientCode,
                                          @CurrentUser LoanUser user) {
        return Result.ok(insightService.generate(clientCode, user));
    }

    /** 复核画像快照：通过则本版本生效，驳回则归档留痕（主管及以上）。 */
    @PostMapping("/{clientCode}/insight/{snapshotNo}/review")
    @OpLog(bizType = "客户画像", action = "REVIEW")
    public Result<Void> reviewInsight(@PathVariable String clientCode,
                                      @PathVariable String snapshotNo,
                                      @RequestBody(required = false) InsightReviewRequest request,
                                      @CurrentUser LoanUser user) {
        insightService.review(clientCode, snapshotNo,
                request == null ? null : request.getDecision(),
                request == null ? null : request.getRemark(), user);
        return Result.ok();
    }
}
