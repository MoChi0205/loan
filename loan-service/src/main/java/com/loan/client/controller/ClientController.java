package com.loan.client.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.client.model.ClientUpdateRequest;
import com.loan.client.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客户档案 HTTP 接口（管理端：轻量查询切片 + P0-6 档案详情 / 编辑）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/client")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    /**
     * 客户轻量分页（关键字：编码 / 联系人 / 企业 / 手机号）。
     *
     * @param keyword 关键字（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 客户轻量列表
     */
    @GetMapping("/page-lite")
    public Result<PageResult<Map<String, Object>>> pageLite(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(clientService.pageLite(keyword, page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 档案合并视图（P0-6）：基础信息 + 企业 + 个人档案 + 认证状态 + 邀请链 + VIP + 审计字段。
     *
     * @param clientCode 客户编码
     * @return 档案合并视图
     */
    @GetMapping("/{clientCode}")
    public Result<Map<String, Object>> detail(@PathVariable String clientCode) {
        return Result.ok(clientService.getClientDetail(clientCode));
    }

    /**
     * 档案编辑（P0-6）：基础信息 + 个人档案字段合并更新，敏感字段加密落库、读取脱敏。
     *
     * @param clientCode 客户编码
     * @param req        编辑请求
     * @return 更新后的档案合并视图
     */
    @PutMapping("/{clientCode}")
    public Result<Map<String, Object>> update(@PathVariable String clientCode,
                                              @RequestBody ClientUpdateRequest req) {
        return Result.ok(clientService.updateClientDetail(clientCode, req));
    }
}
