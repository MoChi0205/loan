package com.loan.lead.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.lead.entity.Lead;
import com.loan.lead.service.LeadService;
import com.loan.log.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 线索 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/lead")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    /**
     * 新增线索。
     *
     * @param lead 线索
     * @param user 当前用户
     * @return 线索编号（业务唯一ID）
     */
    @PostMapping
    @OpLog(bizType = "线索", action = "CREATE")
    public Result<String> create(@RequestBody Lead lead, @CurrentUser LoanUser user) {
        return Result.ok(leadService.create(lead, user == null ? null : user.getUserNo(),
                user == null ? "system" : user.getName()));
    }

    /**
     * 分页查询线索（ownerStaffId 传 0 或空查公海）。
     *
     * @param pool        是否公海（true 查公海）
     * @param leadType    客群
     * @param followStatus 跟进状态
     * @param keyword     关键字
     * @param page        页码
     * @param size        每页大小
     * @param user        当前用户
     * @return 线索分页
     */
    @GetMapping("/page")
    public Result<PageResult<Lead>> page(
            @RequestParam(defaultValue = "false") boolean pool,
            @RequestParam(required = false) String leadType,
            @RequestParam(required = false) String followStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @CurrentUser LoanUser user) {
        // 渠道沙箱强制本人隔离：渠道按录入主体查询而不是按归属人查询（渠道线索归属为空）。
        boolean isChannel = user != null && LoanUser.TYPE_CHANNEL.equals(user.getUserType());
        if (isChannel) {
            return Result.ok(leadService.pageByRecorder(user.getUserNo(), leadType, followStatus, keyword,
                    page, size, orderBy, orderDir));
        }
        String ownerNo;
        ownerNo = pool ? null : (user == null ? null : user.getUserNo());
        String roleCode = user == null ? null : user.getRoleCode();
        String userNo = user == null ? null : user.getUserNo();
        return Result.ok(leadService.page(ownerNo, leadType, followStatus, keyword, page, size, roleCode, userNo, orderBy, orderDir));
    }

    /**
     * 公海认领。
     *
     * @param body { leadNo }
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/claim")
    @OpLog(bizType = "线索", action = "CLAIM")
    public Result<String> claim(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        leadService.claim(body.get("leadNo"), user == null ? null : user.getUserNo(),
                user == null ? "system" : user.getName());
        return Result.ok("ok");
    }

    /**
     * 手动指派。
     *
     * @param body { leadNo, toStaffCode }
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/assign")
    @OpLog(bizType = "线索", action = "ASSIGN")
    public Result<String> assign(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        leadService.assign(body.get("leadNo"), body.get("toStaffCode"),
                user == null ? "system" : user.getName());
        return Result.ok("ok");
    }

    /**
     * 批量认领（公海 → 我的线索）。
     *
     * @param body { leadNos: [..] }
     * @param user 当前用户
     * @return 成功条数
     */
    @PostMapping("/batch-claim")
    @OpLog(bizType = "线索", action = "BATCH_CLAIM")
    public Result<Integer> batchClaim(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        @SuppressWarnings("unchecked")
        List<String> leadNos = (List<String>) body.get("leadNos");
        int count = leadService.batchClaim(leadNos, user == null ? null : user.getUserNo(),
                user == null ? "system" : user.getName());
        return Result.ok(count);
    }

    /**
     * 批量指派。
     *
     * @param body { leadNos: [..], toStaffCode }
     * @param user 当前用户
     * @return 成功条数
     */
    @PostMapping("/batch-assign")
    @OpLog(bizType = "线索", action = "BATCH_ASSIGN")
    public Result<Integer> batchAssign(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        @SuppressWarnings("unchecked")
        List<String> leadNos = (List<String>) body.get("leadNos");
        String toStaffCode = (String) body.get("toStaffCode");
        int count = leadService.batchAssign(leadNos, toStaffCode, user == null ? "system" : user.getName());
        return Result.ok(count);
    }

    /**
     * 批量删除线索（物理删除 + 审计留痕）。
     *
     * @param body { leadNos: [..] }
     * @param user 当前用户
     * @return 成功条数
     */
    @PostMapping("/batch-delete")
    @OpLog(bizType = "线索", action = "BATCH_DELETE")
    public Result<Integer> batchDelete(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        @SuppressWarnings("unchecked")
        List<String> leadNos = (List<String>) body.get("leadNos");
        int count = leadService.batchDelete(leadNos, user == null ? "system" : user.getName());
        return Result.ok(count);
    }

    /**
     * 手动触发回收（阶段一临时；正式用定时任务）。
     *
     * @return 回收数量
     */
    @PostMapping("/recycle")
    public Result<Integer> recycle() {
        return Result.ok(leadService.recycleOverdue());
    }

    /**
     * 手动触发回收预警（阶段一临时；正式用定时任务）。
     *
     * @return 预警发送数
     */
    @PostMapping("/warn-recycle")
    public Result<Integer> warnRecycle() {
        return Result.ok(leadService.warnRecycle());
    }
}
