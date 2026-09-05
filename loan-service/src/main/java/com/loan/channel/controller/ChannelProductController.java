package com.loan.channel.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.mini.service.MiniProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 合作渠道 Web 工作区：本人产品与审批进度。 */
@RestController
@RequestMapping("/api/channel/product")
@RequiredArgsConstructor
public class ChannelProductController {

    private final MiniProductService miniProductService;

    /** 查询当前渠道账号录入的全部产品申请。 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@CurrentUser LoanUser user) {
        return Result.ok(miniProductService.myProducts(user.getUserId()));
    }

    /** 查询本人产品申请详情。 */
    @GetMapping("/{approvalNo}")
    public Result<Map<String, Object>> detail(@PathVariable String approvalNo,
                                              @CurrentUser LoanUser user) {
        return Result.ok(miniProductService.detail(approvalNo, user));
    }

    /** 保存本人产品草稿；所属渠道由登录态决定。 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              @CurrentUser LoanUser user) {
        return Result.ok(miniProductService.save(body, user));
    }

    /** 编辑本人草稿或驳回后的产品。 */
    @PutMapping("/{approvalNo}")
    public Result<Map<String, Object>> update(@PathVariable String approvalNo,
                                              @RequestBody Map<String, Object> body,
                                              @CurrentUser LoanUser user) {
        return Result.ok(miniProductService.update(approvalNo, body, user));
    }

    /** 提交平台审批。 */
    @PostMapping("/{approvalNo}/submit")
    public Result<Void> submit(@PathVariable String approvalNo, @CurrentUser LoanUser user) {
        miniProductService.submit(approvalNo, user);
        return Result.ok();
    }

    /** 撤销待审批申请。 */
    @PostMapping("/{approvalNo}/revoke")
    public Result<Void> revoke(@PathVariable String approvalNo, @CurrentUser LoanUser user) {
        miniProductService.revoke(approvalNo, user);
        return Result.ok();
    }

    /** 申请删除已上架产品。 */
    @PostMapping("/{approvalNo}/delete-apply")
    public Result<Void> applyDelete(@PathVariable String approvalNo,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    @CurrentUser LoanUser user) {
        String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        miniProductService.applyDelete(approvalNo, reason, user);
        return Result.ok();
    }

    /** 撤销待删除申请。 */
    @PostMapping("/{approvalNo}/delete-cancel")
    public Result<Void> cancelDelete(@PathVariable String approvalNo, @CurrentUser LoanUser user) {
        miniProductService.cancelDelete(approvalNo, user);
        return Result.ok();
    }
}
