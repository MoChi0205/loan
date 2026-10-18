package com.loan.client.pool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.LoanUser;
import com.loan.context.CurrentUser;
import com.loan.exception.BusinessException;
import com.loan.importing.service.ImportRoleGuard;
import com.loan.common.util.BizIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资源池管理：创建 / 列表 / 启停。导入任务通过 poolCode 与资源池绑定。
 */
@RestController
@RequestMapping("/api/admin/resource-pool")
@RequiredArgsConstructor
public class ResourcePoolController {

    private final ResourcePoolMapper resourcePoolMapper;
    private final ImportRoleGuard roleGuard;

    /** 资源池分页（含停用，便于管理）；下拉选择用 list 全量接口。 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        Page<ResourcePool> result = resourcePoolMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ResourcePool>().orderByAsc(ResourcePool::getId));
        return Result.ok(PageResult.build(page, size, result.getTotal(), result.getRecords().stream()
                .map(ResourcePoolController::toView).collect(java.util.stream.Collectors.toList())));
    }

    /** 全量启用池（导入目标池 / 线索公海、客户列表筛选下拉）：全体员工可读。 */
    @GetMapping("/list")
    public Result<java.util.List<Map<String, Object>>> list(@CurrentUser LoanUser user) {
        roleGuard.requireStaff(user);
        return Result.ok(resourcePoolMapper.selectList(new LambdaQueryWrapper<ResourcePool>()
                        .eq(ResourcePool::getStatus, "ACTIVE").orderByAsc(ResourcePool::getId))
                .stream().map(ResourcePoolController::toView).collect(java.util.stream.Collectors.toList()));
    }

    /** 创建资源池。 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        String name = body == null ? null : body.get("poolName");
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "资源池名称必填");
        }
        String type = body.getOrDefault("poolType", "COMPANY");
        if (!"COMPANY".equals(type) && !"TEAM".equals(type)) {
            throw new BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "资源池类型不合法");
        }
        String deptCode = body.get("deptCode");
        if ("TEAM".equals(type) && !org.springframework.util.StringUtils.hasText(deptCode)) {
            throw new BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "团队池必须关联部门");
        }
        ResourcePool pool = new ResourcePool();
        pool.setPoolCode(BizIdGenerator.generate("pool"));
        pool.setPoolName(name.trim());
        pool.setPoolType(type);
        pool.setDeptCode("TEAM".equals(type) ? deptCode : null);
        pool.setDeptName("TEAM".equals(type) ? body.get("deptName") : null);
        pool.setDescription(body.get("description"));
        pool.setStatus("ACTIVE");
        pool.setCreatedBy(user.getUserNo());
        pool.setUpdatedBy(user.getUserNo());
        pool.setCreatedAt(LocalDateTime.now());
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.insert(pool);
        return Result.ok(toView(pool));
    }

    /** 启用 / 停用（停用后不再出现在导入目标池下拉，历史绑定不受影响）。 */
    @PostMapping("/{poolCode}/toggle")
    public Result<Void> toggle(@PathVariable String poolCode, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        ResourcePool pool = resourcePoolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                .eq(ResourcePool::getPoolCode, poolCode));
        if (pool == null) throw new BusinessException(com.loan.common.ResultCode.DATA_NOT_FOUND, "资源池不存在");
        pool.setStatus("ACTIVE".equals(pool.getStatus()) ? "INACTIVE" : "ACTIVE");
        pool.setUpdatedBy(user.getUserNo());
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.updateById(pool);
        return Result.ok();
    }

    private static Map<String, Object> toView(ResourcePool pool) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("poolCode", pool.getPoolCode());
        view.put("poolName", pool.getPoolName());
        view.put("poolType", pool.getPoolType());
        view.put("deptCode", pool.getDeptCode());
        view.put("deptName", pool.getDeptName());
        view.put("description", pool.getDescription());
        view.put("status", pool.getStatus());
        view.put("createdAt", pool.getCreatedAt());
        return view;
    }
}
