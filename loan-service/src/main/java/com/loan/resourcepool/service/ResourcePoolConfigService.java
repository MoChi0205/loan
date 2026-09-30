package com.loan.resourcepool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientRecycleConfig;
import com.loan.client.mapper.ClientRecycleConfigMapper;
import com.loan.resourcepool.entity.ResourcePoolConfig;
import com.loan.resourcepool.mapper.ResourcePoolConfigMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.org.entity.Department;
import com.loan.org.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 资源池规则读取：员工池优先，团队池次之，公司公共池兜底。 */
@Service
@RequiredArgsConstructor
public class ResourcePoolConfigService {
    private final ResourcePoolConfigMapper mapper;
    private final ClientRecycleConfigMapper legacyMapper;
    private final StaffMapper staffMapper;
    private final DepartmentMapper departmentMapper;

    public ResourcePoolConfig resolve(String staffCode, String deptCode) {
        ResourcePoolConfig row = null;
        if (StringUtils.hasText(staffCode)) {
            row = mapper.selectOne(new LambdaQueryWrapper<ResourcePoolConfig>()
                    .eq(ResourcePoolConfig::getScopeType, "STAFF")
                    .eq(ResourcePoolConfig::getScopeKey, staffCode)
                    .eq(ResourcePoolConfig::getEnabled, 1).last("LIMIT 1"));
        }
        if (row == null && StringUtils.hasText(deptCode)) {
            row = mapper.selectOne(new LambdaQueryWrapper<ResourcePoolConfig>()
                    .eq(ResourcePoolConfig::getScopeType, "TEAM")
                    .eq(ResourcePoolConfig::getScopeKey, deptCode)
                    .eq(ResourcePoolConfig::getEnabled, 1).last("LIMIT 1"));
        }
        if (row == null) {
            row = mapper.selectOne(new LambdaQueryWrapper<ResourcePoolConfig>()
                    .eq(ResourcePoolConfig::getScopeType, "COMPANY")
                    .eq(ResourcePoolConfig::getEnabled, 1).last("LIMIT 1"));
        }
        if (row != null) return row;
        // 兼容已存在的全局配置，避免迁移未执行时改变行为。
        ClientRecycleConfig legacy = legacyMapper.selectOne(new LambdaQueryWrapper<ClientRecycleConfig>()
                .eq(ClientRecycleConfig::getConfigKey, "GLOBAL").last("LIMIT 1"));
        if (legacy == null) return null;
        ResourcePoolConfig fallback = new ResourcePoolConfig();
        fallback.setPoolCode("COMPANY"); fallback.setPoolName("公司公共资源池"); fallback.setScopeType("COMPANY");
        fallback.setEnabled(1); fallback.setRecycleEnabled(legacy.getRecycleEnabled()); fallback.setRecycleDays(legacy.getRecycleDays());
        fallback.setWarnDays(legacy.getWarnDays()); fallback.setCooldownDays(legacy.getCooldownDays());
        return fallback;
    }

    public List<ResourcePoolConfig> list() {
        return mapper.selectList(new LambdaQueryWrapper<ResourcePoolConfig>().orderByAsc(ResourcePoolConfig::getScopeType)
                .orderByAsc(ResourcePoolConfig::getScopeKey));
    }

    public void save(ResourcePoolConfig req, String operator) {
        if (req == null || !StringUtils.hasText(req.getPoolCode()) || !StringUtils.hasText(req.getScopeType())) {
            throw new IllegalArgumentException("资源池编码和范围类型不能为空");
        }
        if (req.getEnabled() == null) req.setEnabled(1);
        if (req.getRecycleEnabled() == null) req.setRecycleEnabled(0);
        if (req.getWarnDays() == null) req.setWarnDays(15);
        if (req.getCooldownDays() == null) req.setCooldownDays(7);
        String scope = req.getScopeType().trim().toUpperCase();
        if (!java.util.Arrays.asList("COMPANY", "TEAM", "STAFF").contains(scope)) {
            throw new IllegalArgumentException("资源池范围必须为公司、团队或员工");
        }
        req.setScopeType(scope);
        if ("COMPANY".equals(scope)) req.setScopeKey(null);
        if ("TEAM".equals(scope)) {
            if (!StringUtils.hasText(req.getScopeKey()) || departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                    .eq(Department::getDeptCode, req.getScopeKey()).eq(Department::getStatus, "ACTIVE")) == 0) {
                throw new IllegalArgumentException("团队资源池必须选择有效团队");
            }
        }
        if ("STAFF".equals(scope)) {
            if (!StringUtils.hasText(req.getScopeKey()) || staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                    .eq(Staff::getStaffCode, req.getScopeKey()).eq(Staff::getStatus, "ACTIVE")) == 0) {
                throw new IllegalArgumentException("员工资源池必须选择在职员工");
            }
        }
        if (req.getRecycleEnabled() == 1) {
            if (req.getRecycleDays() == null || req.getRecycleDays() <= 0 || req.getWarnDays() < 0 || req.getCooldownDays() < 0) {
                throw new IllegalArgumentException("回收、预警和冷却天数必须为有效非负数，回收天数必须大于 0");
            }
            Map<String, Integer> days = parseStatusDays(req);
            if (days.containsKey("DEAL") || days.containsKey("SIGNED") || days.containsKey("NO_NEED")) {
                throw new IllegalArgumentException("成交、已签约、无需求客户不可配置自动回收");
            }
        }
        ResourcePoolConfig old = mapper.selectOne(new LambdaQueryWrapper<ResourcePoolConfig>()
                .eq(ResourcePoolConfig::getPoolCode, req.getPoolCode()).last("LIMIT 1"));
        req.setUpdatedBy(operator);
        if (old == null) mapper.insert(req);
        else { req.setId(old.getId()); req.setCreatedAt(old.getCreatedAt()); mapper.updateById(req); }
    }

    public Map<String, Integer> parseStatusDays(ResourcePoolConfig config) {
        if (config == null || !StringUtils.hasText(config.getStatusDaysJson())) return Collections.emptyMap();
        try {
            Map<?, ?> raw = new com.fasterxml.jackson.databind.ObjectMapper().readValue(config.getStatusDaysJson(), Map.class);
            Map<String, Integer> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> { try { int days = Integer.parseInt(String.valueOf(v)); if (days > 0) out.put(String.valueOf(k).toUpperCase(), days); } catch (Exception ignored) {} });
            return out;
        } catch (Exception e) { return Collections.emptyMap(); }
    }
}
