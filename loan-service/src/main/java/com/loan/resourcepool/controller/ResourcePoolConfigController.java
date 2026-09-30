package com.loan.resourcepool.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.infrastructure.security.AdminRoleGuard;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.resourcepool.entity.ResourcePoolConfig;
import com.loan.resourcepool.service.ResourcePoolConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/resource-pool-config")
@RequiredArgsConstructor
public class ResourcePoolConfigController {
    private final ResourcePoolConfigService service;

    @GetMapping("/list")
    public Result<List<ResourcePoolConfig>> list(@CurrentUser LoanUser user) {
        requireResourcePoolAdmin(user);
        return Result.ok(service.list());
    }

    @PutMapping("/save")
    public Result<Void> save(@RequestBody ResourcePoolConfig req, @CurrentUser LoanUser user) {
        requireResourcePoolAdmin(user);
        service.save(req, user.getUserNo());
        return Result.ok();
    }

    private void requireResourcePoolAdmin(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())
                || !("BOSS".equalsIgnoreCase(user.getRoleCode()) || AdminRoleGuard.isSystemConfigAdmin(user))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅老板、运营管理员或超级管理员可维护资源池规则");
        }
    }
}
