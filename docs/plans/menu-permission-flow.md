# 前端菜单绑定与角色权限配置流程（含审批流程）

> 2026-08-26 ｜ 落地：菜单分组 / 动态菜单树 / 权限配置回显 / 审批闭环

## 一、菜单分组（前端侧栏）

侧栏按大业务功能分 5 组（`src/layout/Layout.vue` `menuGroups`）：

| 分组 | 子菜单 |
|------|--------|
| 工作台（常驻） | 我的工作台 |
| 客户经营 | 线索公海 / 服务工单 / 初筛执行 / 报表中心 |
| 产品与规则 | 产品库 / 规则目录 / 报告模板 |
| 运营支撑 | 审批中心 / 短信中心 / 推荐奖励 |
| 系统管理 | 组织权限 / 配置向导 / 风控黑名单 / 调试中心 / 审计中心 |

点击子菜单 → `router.push` → 自动打开多标签 tab（可切换/关闭/刷新，localStorage 持久化）。

## 二、菜单 ↔ 路由 ↔ 权限绑定

- **数据源**：`t_menu`（16 条，path 与前端路由一一对应）+ `t_role_permission`（角色 × 菜单）
- **菜单树接口**：`GET /api/admin/org/menu/tree?roleCode=`（BOSS 全量，其余按 `t_role_permission.menu_id` 过滤）
- **权限回显**：`GET /api/admin/org/permission/list?roleCode=`（返回已授权 menuId）
- **权限保存**：`POST /api/admin/org/permission/save`（先删后插 + `@OpLog` 操作留痕）
- **前端菜单**：Layout `menuGroups` 为展示骨架；权限控制通过 OrgCenter「角色权限」tab 配置，配置后按角色可见性生效（后续可接入路由守卫按菜单过滤）

## 三、角色权限配置流程（管理员）

1. 进入「系统管理 → 组织权限 → 角色权限」tab
2. 选择角色卡片 → 「配置权限」→ 弹窗菜单树**回显**该角色已授权项
3. 勾选/取消菜单与按钮 → 保存（先删后插，`menuIds` 需含祖先菜单）
4. 保存成功提示 + 操作日志留痕（t_operation_log）

## 四、审批流程（业务审批闭环）

| 场景 | 流程 | 状态机 |
|------|------|--------|
| 产品审核 | 渠道提交 → 管理端审核 → 通过入全量库 / 驳回 | PENDING → APPROVED/REJECTED |
| 附件下载审批 | 员工申请 → 主管审批 → 通过生成 24h 限时链接 / 驳回 | PENDING → APPROVED/REJECTED（+ 作废） |
| 奖励结算 | 工单 DEAL 自动结算 → 审核发放 / 驳回 / 作废 | PENDING_AUDIT → GRANTED/REJECTED/VOID |
| 权限变更 | 管理员配置 → `@OpLog` 留痕（含操作人/角色/时间） | 留痕可审计 |

审批人解析：未配置部门审核人时默认老板兜底（`t_dept_approver` 预留，后续迭代）。

## 五、新模块接入清单（后续新增功能时执行）

1. **后端**：`t_menu` 插入菜单（path=前端路由、component=views 路径、sort、ACTIVE）
2. **前端**：`src/router/index.js` 注册路由（meta.title）
3. **前端**：`Layout.vue` `menuGroups` 按业务域加子菜单项
4. **权限**：OrgCenter「角色权限」勾选授权（BOSS 默认全量）
5. **验证**：`mvn compile` + `vite build` + 登录按角色查看菜单

## 相关规范
- `.workbuddy/skills/loan-web-ui/SKILL.md`（多标签 / 菜单分组 / 业务ID展示）
- `.workbuddy/skills/loan-web-dev/SKILL.md`、`.workbuddy/skills/loan-backend/SKILL.md`
