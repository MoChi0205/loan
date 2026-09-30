package com.loan.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.common.ResultCode;
import com.loan.common.cache.UnifiedCacheService;
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
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.entity.ClientFollowRecord;
import com.loan.serviceops.entity.StaffOuting;
import com.loan.serviceops.mapper.ClientAppointmentMapper;
import com.loan.serviceops.mapper.ClientFollowRecordMapper;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.resourcepool.entity.StaffDataTransferRecord;
import com.loan.resourcepool.mapper.StaffDataTransferRecordMapper;
import com.loan.common.util.BizIdGenerator;
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
    private final UnifiedCacheService cacheService;
    private final ClientProfileMapper clientProfileMapper;
    private final LeadMapper leadMapper;
    private final ServiceOrderMapper serviceOrderMapper;
    private final ClientAppointmentMapper appointmentMapper;
    private final ClientFollowRecordMapper followRecordMapper;
    private final StaffOutingMapper outingMapper;
    private final StaffDataTransferRecordMapper transferRecordMapper;

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
        cacheService.evict("org:departments:tree");
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
        cacheService.evict("org:departments:tree");
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
            // Staff.phone 已由 AesTypeHandler 统一加密，业务层只写明文，避免双重 AES。
            s.setPhone(StringUtils.hasText(phone) ? phone : null);
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
                // Staff.phone 已由 AesTypeHandler 统一加密。
                exist.setPhone(phone);
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

    /** 离职员工数据交接：客户/线索进入公司公海或转给指定在职员工，服务历史保留并形成审计记录。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferStaffData(String fromStaffCode, String targetStaffCode, String mode,
                                                  String reason, String operator) {
        if (!StringUtils.hasText(fromStaffCode)) throw new BusinessException(ResultCode.PARAM_ERROR, "原员工工号必填");
        Staff from = staffMapper.selectOne(new LambdaQueryWrapper<Staff>().eq(Staff::getStaffCode, fromStaffCode).last("LIMIT 1"));
        if (from == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "原员工不存在");
        boolean toPool = "COMPANY_POOL".equalsIgnoreCase(mode) || !StringUtils.hasText(targetStaffCode);
        if (!toPool) {
            Staff target = staffMapper.selectOne(new LambdaQueryWrapper<Staff>().eq(Staff::getStaffCode, targetStaffCode)
                    .eq(Staff::getStatus, "ACTIVE").last("LIMIT 1"));
            if (target == null) throw new BusinessException(ResultCode.PARAM_ERROR, "接收员工不存在或已离职");
            if (fromStaffCode.equals(targetStaffCode)) throw new BusinessException(ResultCode.PARAM_ERROR, "接收员工不能与离职员工相同");
        } else targetStaffCode = null;
        int total = 0;
        List<ClientProfile> clients = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>().eq(ClientProfile::getOwnerStaffCode, fromStaffCode));
        for (ClientProfile row : clients) {
            LambdaUpdateWrapper<ClientProfile> w = new LambdaUpdateWrapper<ClientProfile>().eq(ClientProfile::getClientCode, row.getClientCode()).eq(ClientProfile::getOwnerStaffCode, fromStaffCode)
                    .set(ClientProfile::getOwnerStaffCode, targetStaffCode).set(ClientProfile::getUpdatedBy, operator);
            if (toPool) w.set(ClientProfile::getSeaLevel, "COMPANY").set(ClientProfile::getSeaDeptCode, null);
            else w.set(ClientProfile::getSeaLevel, null).set(ClientProfile::getSeaDeptCode, null);
            total += clientProfileMapper.update(null, w);
            audit(fromStaffCode, targetStaffCode, "CLIENT", row.getClientCode(), reason, operator);
        }
        List<Lead> leads = leadMapper.selectList(new LambdaQueryWrapper<Lead>().eq(Lead::getOwnerStaffCode, fromStaffCode));
        for (Lead row : leads) {
            total += leadMapper.update(null, new LambdaUpdateWrapper<Lead>().eq(Lead::getLeadNo, row.getLeadNo()).eq(Lead::getOwnerStaffCode, fromStaffCode)
                    .set(Lead::getOwnerStaffCode, targetStaffCode).set(Lead::getUpdatedBy, operator));
            audit(fromStaffCode, targetStaffCode, "LEAD", row.getLeadNo(), reason, operator);
        }
        transferOrders(fromStaffCode, targetStaffCode, reason, operator);
        transferAppointments(fromStaffCode, targetStaffCode, reason, operator);
        transferOutings(fromStaffCode, targetStaffCode, reason, operator);
        transferFollowRecords(fromStaffCode, targetStaffCode, reason, operator);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("fromStaffCode", fromStaffCode); result.put("toStaffCode", targetStaffCode); result.put("companyPool", toPool); result.put("clientCount", clients.size()); result.put("leadCount", leads.size());
        return result;
    }

    /** 按团队批量执行离职/回收交接，团队内每名员工独立写入审计记录。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferTeamData(String deptCode, String targetStaffCode, String mode,
                                                 String reason, String operator) {
        if (!StringUtils.hasText(deptCode)) throw new BusinessException(ResultCode.PARAM_ERROR, "团队编码必填");
        List<Staff> members = staffMapper.selectList(new LambdaQueryWrapper<Staff>().eq(Staff::getDeptCode, deptCode)
                .ne(Staff::getStatus, "ACTIVE").or(w -> w.eq(Staff::getDeptCode, deptCode).eq(Staff::getStatus, "ACTIVE")));
        int clients = 0, leads = 0;
        for (Staff member : members) {
            if (member.getStaffCode().equals(targetStaffCode)) continue;
            Map<String, Object> one = transferStaffData(member.getStaffCode(), targetStaffCode, mode, reason, operator);
            clients += ((Number) one.get("clientCount")).intValue();
            leads += ((Number) one.get("leadCount")).intValue();
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("deptCode", deptCode); out.put("toStaffCode", targetStaffCode); out.put("clientCount", clients); out.put("leadCount", leads); out.put("staffCount", members.size());
        return out;
    }

    private void transferOrders(String from, String to, String reason, String operator) {
        for (ServiceOrder row : serviceOrderMapper.selectList(new LambdaQueryWrapper<ServiceOrder>().eq(ServiceOrder::getOwnerStaffCode, from))) {
            if (StringUtils.hasText(to)) serviceOrderMapper.update(null, new LambdaUpdateWrapper<ServiceOrder>().eq(ServiceOrder::getOrderNo, row.getOrderNo()).eq(ServiceOrder::getOwnerStaffCode, from).set(ServiceOrder::getOwnerStaffCode, to).set(ServiceOrder::getUpdatedBy, operator));
            audit(from, to, "ORDER", row.getOrderNo(), reason, operator);
        }
    }
    private void transferAppointments(String from, String to, String reason, String operator) {
        for (ClientAppointment row : appointmentMapper.selectList(new LambdaQueryWrapper<ClientAppointment>().eq(ClientAppointment::getHostStaffCode, from))) {
            if (StringUtils.hasText(to)) appointmentMapper.update(null, new LambdaUpdateWrapper<ClientAppointment>().eq(ClientAppointment::getAppointmentNo, row.getAppointmentNo()).eq(ClientAppointment::getHostStaffCode, from).set(ClientAppointment::getHostStaffCode, to).set(ClientAppointment::getUpdatedBy, operator));
            audit(from, to, "APPOINTMENT", row.getAppointmentNo(), reason, operator);
        }
    }
    private void transferOutings(String from, String to, String reason, String operator) {
        for (StaffOuting row : outingMapper.selectList(new LambdaQueryWrapper<StaffOuting>().eq(StaffOuting::getStaffCode, from))) {
            if (StringUtils.hasText(to)) outingMapper.update(null, new LambdaUpdateWrapper<StaffOuting>().eq(StaffOuting::getOutingNo, row.getOutingNo()).eq(StaffOuting::getStaffCode, from).set(StaffOuting::getStaffCode, to).set(StaffOuting::getUpdatedBy, operator));
            audit(from, to, "OUTING", row.getOutingNo(), reason, operator);
        }
    }
    private void transferFollowRecords(String from, String to, String reason, String operator) {
        for (ClientFollowRecord row : followRecordMapper.selectList(new LambdaQueryWrapper<ClientFollowRecord>().eq(ClientFollowRecord::getStaffCode, from))) {
            if (StringUtils.hasText(to)) followRecordMapper.update(null, new LambdaUpdateWrapper<ClientFollowRecord>().eq(ClientFollowRecord::getFollowNo, row.getFollowNo()).eq(ClientFollowRecord::getStaffCode, from).set(ClientFollowRecord::getStaffCode, to).set(ClientFollowRecord::getCreatedBy, operator));
            audit(from, to, "FOLLOW", row.getFollowNo(), reason, operator);
        }
    }
    private void audit(String from, String to, String type, String code, String reason, String operator) {
        StaffDataTransferRecord r = new StaffDataTransferRecord(); r.setTransferNo(BizIdGenerator.generate("transfer")); r.setFromStaffCode(from); r.setToStaffCode(to); r.setBusinessType(type); r.setBusinessCode(code); r.setReason(reason); r.setOperator(operator); r.setCreatedAt(LocalDateTime.now()); transferRecordMapper.insert(r);
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
        cacheService.evict("org:menus:" + roleCode.trim().toUpperCase());
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
