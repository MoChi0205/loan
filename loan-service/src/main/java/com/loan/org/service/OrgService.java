package com.loan.org.service;
import com.loan.common.util.PageOrder;
import com.loan.common.cache.UnifiedCacheService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.org.entity.Department;
import com.loan.org.entity.Menu;
import com.loan.org.entity.Role;
import com.loan.org.entity.RolePermission;
import com.loan.org.mapper.DepartmentMapper;
import com.loan.org.mapper.MenuMapper;
import com.loan.org.mapper.RoleMapper;
import com.loan.org.mapper.RolePermissionMapper;
import com.loan.org.vo.MenuNodeVO;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 组织权限服务：菜单树（按角色）/ 部门树 / 角色列表 / 员工分页。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<Staff, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("id", Staff::getId);
        ORDER_FIELDS.put("createdAt", Staff::getCreatedAt);
    }

    private final MenuMapper menuMapper;
    private final DepartmentMapper departmentMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final StaffMapper staffMapper;
    private final UnifiedCacheService cacheService;

    private static final TypeReference<List<MenuNodeVO>> MENU_LIST_TYPE =
            new TypeReference<List<MenuNodeVO>>() { };
    private static final TypeReference<List<Role>> ROLE_LIST_TYPE =
            new TypeReference<List<Role>>() { };

    /**
     * 按角色查询菜单树（所有角色统一走 t_role_permission 数据）。
     *
     * <p>D24 修正：原 BOSS 特判"全放行"绕过权限表，导致调试中心等收窄项对 BOSS 可见；
     * 现 BOSS 同样由 t_role_permission 驱动；业务菜单保留，组织权限、系统配置和调试中心不授予。
     *
     * @param roleCode 角色编码
     * @return 菜单树
     */
    public List<MenuNodeVO> listMenusByRole(String roleCode) {
        final String cacheKey = "org:menus:" + (roleCode == null ? "" : roleCode.trim().toUpperCase());
        List<MenuNodeVO> cached = cacheService.getOrLoad(cacheKey, MENU_LIST_TYPE,
                () -> loadMenusByRole(roleCode));
        return cached == null ? Collections.<MenuNodeVO>emptyList() : cached;
    }

    /** 从数据库构建角色菜单树（仅由缓存门面回源调用）。 */
    private List<MenuNodeVO> loadMenusByRole(String roleCode) {
        List<Menu> all = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getStatus, "ACTIVE")
                        .orderByAsc(Menu::getSort));
        // 所有角色：查角色权限里的 menu_id 集合
        List<RolePermission> perms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleCode, roleCode));
        Set<Long> allowedIds = perms.stream().map(RolePermission::getMenuId).collect(Collectors.toSet());
        return buildTree(all, allowedIds, false);
    }

    /**
     * 部门树（按部门编码 parentCode 串联）。
     *
     * @return 部门树（嵌套）
     */
    public List<MenuNodeVO> listDepartmentTree() {
        final String cacheKey = "org:departments:tree";
        List<MenuNodeVO> cached = cacheService.getOrLoad(cacheKey, MENU_LIST_TYPE,
                this::loadDepartmentTree);
        return cached == null ? Collections.<MenuNodeVO>emptyList() : cached;
    }

    /** 从数据库构建部门树。 */
    private List<MenuNodeVO> loadDepartmentTree() {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .eq(Department::getStatus, "ACTIVE")
                        .orderByAsc(Department::getSort));
        Map<String, MenuNodeVO> nodeMap = all.stream().collect(Collectors.toMap(
                Department::getDeptCode,
                d -> {
                    MenuNodeVO node = new MenuNodeVO();
                    node.setId(d.getId());
                    node.setParentCode(d.getParentCode());
                    node.setName(d.getDeptName());
                    node.setCode(d.getDeptCode());
                    node.setChildren(new ArrayList<>());
                    return node;
                }));
        List<MenuNodeVO> roots = new ArrayList<>();
        for (MenuNodeVO node : nodeMap.values()) {
            if (node.getParentCode() == null || !nodeMap.containsKey(node.getParentCode())) {
                roots.add(node);
            } else {
                MenuNodeVO parent = nodeMap.get(node.getParentCode());
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 角色列表。
     *
     * @return 角色列表
     */
    public List<Role> listRoles() {
        List<Role> cached = cacheService.getOrLoad("org:roles", ROLE_LIST_TYPE, () -> roleMapper.selectList(
                new LambdaQueryWrapper<Role>().eq(Role::getStatus, "ACTIVE").orderByAsc(Role::getId)));
        return cached == null ? Collections.<Role>emptyList() : cached;
    }

    /**
     * 角色已授权菜单 ID 列表（权限配置回显）。
     *
     * @param roleCode 角色编码
     * @return 已授权 menu_id 列表
     */
    public List<Long> listRolePermissionMenuIds(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return Collections.emptyList();
        }
        return rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>()
                                .eq(RolePermission::getRoleCode, roleCode)
                                .isNotNull(RolePermission::getMenuId)
                                .gt(RolePermission::getMenuId, 0))
                .stream().map(RolePermission::getMenuId).collect(Collectors.toList());
    }

    /**
     * 员工分页查询（含部门名 / 角色名）。
     *
     * @param deptCode  部门编码（可选，业务编码）
     * @param roleCode  角色编码（可选）
     * @param keyword   姓名/工号关键字（可选）
     * @param page      页码
     * @param size      每页大小
     * @return 员工分页结果
     */
    public PageResult<Map<String, Object>> pageStaff(String deptCode, String roleCode, String keyword, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<>();
        if (deptCode != null && !deptCode.isEmpty()) {
            wrapper.eq(Staff::getDeptCode, deptCode);
        }
        if (roleCode != null && !roleCode.isEmpty()) {
            wrapper.eq(Staff::getRoleCode, roleCode);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Staff::getStaffName, keyword).or().like(Staff::getStaffCode, keyword));
        }
        wrapper.eq(Staff::getStatus, "ACTIVE").orderByAsc(Staff::getId);

        Page<Staff> result = staffMapper.selectPage(new Page<>(page, size), wrapper);

        // 部门名 / 角色名映射（按部门编码）
        Map<String, String> deptMap = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getDeptCode, Department::getDeptName));
        Map<String, String> roleMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getRoleCode, Role::getRoleName));

        List<Map<String, Object>> records = result.getRecords().stream().map(s -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("staffCode", s.getStaffCode());
            m.put("staffName", s.getStaffName());
            m.put("deptCode", s.getDeptCode());
            m.put("deptName", s.getDeptCode() == null ? null : deptMap.get(s.getDeptCode()));
            m.put("roleCode", s.getRoleCode());
            m.put("roleName", roleMap.get(s.getRoleCode()));
            m.put("phone", DesensitizeUtils.phone(com.loan.infrastructure.security.AesUtils.decrypt(s.getPhone())));
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 构建菜单树。
     *
     * @param all       全部菜单
     * @param allowedIds 允许的菜单 ID 集合（BOSS 传空集表示全放行）
     * @param allAccess 是否全放行
     * @return 菜单树
     */
    private List<MenuNodeVO> buildTree(List<Menu> all, Set<Long> allowedIds, boolean allAccess) {
        Map<Long, MenuNodeVO> nodeMap = all.stream()
                .filter(m -> allAccess || allowedIds.contains(m.getId()))
                .collect(Collectors.toMap(
                        Menu::getId,
                        m -> {
                            MenuNodeVO node = new MenuNodeVO();
                            node.setId(m.getId());
                            node.setParentId(m.getParentId());
                            node.setName(m.getMenuName());
                            node.setCode(m.getPath());
                            node.setComponent(m.getComponent());
                            node.setType(m.getMenuType());
                            node.setPermissionCode(m.getPermissionCode());
                            node.setCustomerGroup(m.getCustomerGroup());
                            node.setSort(m.getSort());
                            node.setChildren(new ArrayList<>());
                            return node;
                        }));
        List<MenuNodeVO> roots = new ArrayList<>();
        for (MenuNodeVO node : nodeMap.values()) {
            if (node.getParentId() == null) {
                roots.add(node);
            } else {
                MenuNodeVO parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        return roots;
    }
}
