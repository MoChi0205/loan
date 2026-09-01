package com.loan.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.org.entity.Department;
import com.loan.org.entity.Role;
import com.loan.org.entity.RolePermission;
import com.loan.org.mapper.DepartmentMapper;
import com.loan.org.mapper.RoleMapper;
import com.loan.org.mapper.RolePermissionMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 组织权限写服务：部门管理 / 员工管理 / 角色权限保存。
 *
 * <p>员工手机号 AES 加密落库（复用 AesUtils）；离职停用联动后续转移提醒（阶段二⑥后续迭代）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class OrgWriteService {

    private final DepartmentMapper departmentMapper;
    private final StaffMapper staffMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    // ============================================================
    // 部门管理
    // ============================================================

    /**
     * 新增 / 编辑部门（deptCode 唯一；parentCode 引用上级部门编码）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDepartment(Department req, String operator) {
        if (!StringUtils.hasText(req.getDeptCode()) || !StringUtils.hasText(req.getDeptName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "部门编码与名称必填");
        }
        if (StringUtils.hasText(req.getParentCode()) && req.getParentCode().equals(req.getDeptCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上级部门不能是自己");
        }
        Department exist = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getDeptCode, req.getDeptCode()));
        if (exist == null) {
            req.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "ACTIVE");
            req.setSort(req.getSort() == null ? 0 : req.getSort());
            req.setCreatedBy(operator);
            req.setUpdatedBy(operator);
            departmentMapper.insert(req);
        } else {
            exist.setDeptName(req.getDeptName());
            exist.setParentCode(req.getParentCode());
            exist.setLeaderStaffCode(req.getLeaderStaffCode());
            exist.setSort(req.getSort());
            exist.setUpdatedBy(operator);
            departmentMapper.updateById(exist);
        }
    }

    /**
     * 停用部门（置 DISABLED）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableDepartment(String deptCode, String operator) {
        Department exist = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getDeptCode, deptCode));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "部门不存在");
        }
        exist.setStatus("DISABLED");
        exist.setUpdatedBy(operator);
        departmentMapper.updateById(exist);
    }

    // ============================================================
    // 员工管理
    // ============================================================

    /**
     * 新增 / 编辑员工（staffCode / crmUserId 唯一；手机号 AES 加密）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveStaff(Map<String, Object> req, String operator) {
        String staffCode = str(req.get("staffCode"));
        String crmUserId = str(req.get("crmUserId"));
        String staffName = str(req.get("staffName"));
        String roleCode = str(req.get("roleCode"));
        if (!StringUtils.hasText(staffCode) || !StringUtils.hasText(crmUserId)
                || !StringUtils.hasText(staffName) || !StringUtils.hasText(roleCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "工号/CRM员工ID/姓名/角色必填");
        }
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleCode));
        if (role == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色不存在");
        }
        Staff exist = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode));
        String phone = str(req.get("phone"));
        String phoneHash = StringUtils.hasText(phone) ? sha256(phone) : null;
        if (exist == null) {
            // 校验 CRM ID 唯一
            Long dup = staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                    .eq(Staff::getCrmUserId, crmUserId));
            if (dup != null && dup > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "CRM 员工 ID 已存在");
            }
            Staff s = new Staff();
            s.setStaffCode(staffCode);
            s.setCrmUserId(crmUserId);
            s.setStaffName(staffName);
            s.setDeptCode(str(req.get("deptCode")));
            s.setRoleCode(roleCode);
            s.setWecomQrCode(str(req.get("wecomQrCode")));
            s.setPhone(StringUtils.hasText(phone) ? AesUtils.encrypt(phone) : null);
            s.setPhoneHash(phoneHash);
            s.setStatus("ACTIVE");
            s.setCreatedBy(operator);
            s.setUpdatedBy(operator);
            staffMapper.insert(s);
        } else {
            exist.setStaffName(staffName);
            exist.setDeptCode(str(req.get("deptCode")));
            exist.setRoleCode(roleCode);
            exist.setWecomQrCode(str(req.get("wecomQrCode")));
            if (StringUtils.hasText(phone)) {
                exist.setPhone(AesUtils.encrypt(phone));
                exist.setPhoneHash(phoneHash);
            }
            exist.setUpdatedBy(operator);
            staffMapper.updateById(exist);
        }
    }

    /**
     * 员工离职停用（置 LEAVE）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableStaff(String staffCode, String operator) {
        Staff exist = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在");
        }
        exist.setStatus("LEAVE");
        exist.setLeaveTime(LocalDateTime.now());
        exist.setUpdatedBy(operator);
        staffMapper.updateById(exist);
    }

    // ============================================================
    // 角色权限保存
    // ============================================================

    /**
     * 保存角色权限（先删后插，仅补选中项，祖先由前端 expandWithAncestors 带入）。
     *
     * @param roleCode   角色编码
     * @param menuIds    菜单/按钮 ID 列表
     * @param permissionCodes 操作权限码列表
     * @param operator   操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRolePermission(String roleCode, List<Long> menuIds, List<String> permissionCodes, String operator) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色编码必填");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleCode, roleCode));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleCode(roleCode);
                rp.setMenuId(menuId);
                rp.setPermissionCode(null);
                rp.setCreatedBy(operator);
                rolePermissionMapper.insert(rp);
            }
        }
        if (permissionCodes != null) {
            for (String pc : permissionCodes) {
                if (!StringUtils.hasText(pc)) {
                    continue;
                }
                RolePermission rp = new RolePermission();
                rp.setRoleCode(roleCode);
                rp.setMenuId(0L);
                rp.setPermissionCode(pc);
                rp.setCreatedBy(operator);
                rolePermissionMapper.insert(rp);
            }
        }
    }

    /** 取字符串（容错 null）。 */
    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** SHA-256 哈希。 */
    private String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}
