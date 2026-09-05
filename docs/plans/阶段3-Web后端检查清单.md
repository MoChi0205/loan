# 阶段 3：Web 后端（loan-service）检查清单

> 审查人：Architect｜日期：2026-08-30
> 审查范围：`loan-service/src/main/java/com/loan/`（40 个包，330+ Java）
> 审查基准：知识库 00（四阶段规划）/ 02（红线）/ 03（数据模型）/ 04（API 契约）+ `docs/plans/阶段二后端验收清单.md` + 前端 `loan-mini/api/*` 交叉核对
> 结论：**发现 1 个 P0 级全局安全缺陷（admin 管理端整体无鉴权/无角色权限，匿名可调用）；其余红线（XXL-Job/BizId/敏感字段/操作日志）均合规；阶段二验收清单已明显过时，需回写**

---

## 一、模块现状总览（对照验收清单，2026-08-26 → 现在）

> 验收清单判定口径：✅ 完成 / 🟡 部分 / 🩲 仅骨架 / ⬜ 未开始。以下为**当前实际代码**复核结果。

### 1.1 清单标注「空目录」，但当前已有实现（清单已过时，需回写）

| 功能域 | 验收清单（08-26） | 当前实际（08-30） | 备注 |
|--------|-------------------|-------------------|------|
| 服务工单（#9） | ⬜ `order/` 空 | ✅ 有 `OrderController/Service` + `ServiceOrder` 实体：建单/分页/详情/状态机（NEW→IN_SERVICE→DEAL/CANCEL，DEAL→REFUND）+ DEAL 联动奖励结算 | 但**无权限守卫**（见 B1/B3） |
| 资料下载审批（#10） | ⬜ `approval/` 空 | ✅ 有 `ApprovalController/Service` + 产品审核 + 附件下载审批（apply/page/audit/void，24h 链接） | 无守卫 |
| 奖励结算中心（#11） | ⬜ `reward/` 空 | ✅ 有 `RewardController/Service` + RewardRule/RewardRecord：DEAL 自动结算/分页/规则 CRUD | 无守卫；**规则比例可被任意人改** |
| 客户黑名单（#6 子项） | ⬜ `blacklist/` 空 | ✅ 有 `BlacklistController/Service` + 多维命中/新增/解禁 | 无守卫 |
| 我的工作台（#1） | ⬜ `dashboard/` 空 | 🟡 有 `DashboardController/Service`（2 文件，聚合卡） | 无守卫 |
| 配置/计划域（新增） | 未在清单 | 🟡 `plan/` **27 个文件**（AdmissionExecutionPlan/StrategyTemplate/ChannelUserList/ProductStrategy 四块实体+Mapper+Service+Controller，`/api/admin/execution-plan`、`/strategy-template`、`/channel-user-list`、`/channel-strategy`） | 清单外新增功能域；无守卫 |

### 1.2 仍为空占位（⬜ 未开始，与清单一致）
- `vip/`、`stats/`（报表中心）、`oss/`、`match/`、`job/`（任务 handler 实际分散在 lead/partner 包）、`interceptor/`、`enums/`、`crm/` —— 8 个目录 0 Java 文件。

### 1.3 其它域（清单 🟡 项复核）
| 功能域 | 现状 | 备注 |
|--------|------|------|
| 组织权限中心（#2） | 🟡 `org/` 12 文件（OrgController + OrgService + OrgWriteService + 员工映射 + 菜单树） | 较清单有进展（补了写接口 OrgWriteService）；无守卫 |
| 审计中心（#4） | 🟡 `audit/` 6 文件（匹配过程审计） + `log/` 操作日志切面已就绪 | 操作日志 t_operation_log **已落库**（清单称"未接入"已过时） |
| 渠道用户+产品审核（#7） | 🟡 `channel/` 仅 ChannelUser 实体+Mapper；产品 CRUD 在 product/；`plan/ChannelUserList` 为渠道名单（新） | 渠道用户管理 CRUD 仍缺 |
| 线索公海（#8） | ✅ `lead/` 7 文件：录入/page/claim/assign/recycle/warn-recycle + `@XxlJob leadRecycleJob` | 回收规则参数配置写接口仍缺 |
| 短信中心（#12） | 🟡 `sms/` 8 文件：验证码 send/verify + SmsAdmin 管理 | 模板/回执/频控/退订缺 |
| 报告模板（#14） | 🟡 `report/` 11 文件：ReportTemplateController 已存在 | 较清单有进展 |
| 初筛闸门（#13） | ⬜ 无实现 | 一致 |
| 敏感授权（清单附加） | ✅ `sensitive/` 8 文件：apply-view/quota | **唯一明确按角色脱敏/授权的模块** |

---

## 二、安全与权限（★ 核心问题）

### B1【P0】管理端（/api/admin/**）整体无鉴权、无角色权限控制 —— 匿名可调用
- **问题链路**：
  1. `config/SecurityConfig.java`：`.authorizeRequests().anyRequest().permitAll()` —— **Spring Security 对所有请求放行**（仅关闭 CSRF/会话），注释自认「阶段一最小闭环保持接口可访问，业务层后续按需加权限」；
  2. `infrastructure/filter/JwtAuthenticationFilter`：白名单外路径**不拒绝**，仅「有 token 则填充 UserContext」，无 token 也放行；
  3. `infrastructure/aspect/CurrentUserAspect`：`@CurrentUser` 未登录注入 **null，不拒绝**（注释「由后续鉴权逻辑决定是否拒绝」）；
  4. **admin 控制器普遍无守卫**：grep 全库 `FORBIDDEN` 仅出现在 `mini/*`（requireStaff/requireChannel/requireApprover）、`partner/PartnerProductController`、`apiperm`、`report/ReportQueryService`；**`order/approval/reward/blacklist/dashboard/plan/product/lead/sms/sensitive/org/audit` 等 admin 控制器全部没有 FORBIDDEN 类守卫**，且普遍使用 `user == null ? "system" : ...` 兜底（把"未登录"当"system"继续执行）。
- **影响**：未登录匿名、以及登录的 CUSTOMER / CHANNEL 用户，均可直接调用管理接口，例如：
  - `POST /api/admin/order`（建单）、`PUT /api/admin/order/{no}/status`（流转成交 → **触发奖励结算**）
  - `POST /api/admin/approval/product/{no}/audit`（审核产品上架/驳回）、`POST /api/admin/approval/download/{no}/audit`（批准下载 → 生成 24h 无水印链接）
  - `POST /api/admin/blacklist` / `release`（新增/解禁黑名单）
  - `POST /api/admin/reward/rule`（**修改奖励比例**）、`/rule/{id}/disable`
  - `plan/*`（执行计划/策略模板写接口）、`sms/*`、`product/*`（产品 CRUD）
- **期望**：① SecurityConfig 改为 `anyRequest().authenticated()` + `/api/admin/**` 角色门槛（STAFF）；② 引入统一权限拦截（apiperm 已有表/接口但**无运行时拦截器**，需落地 enforcement）；③ 各 admin 写接口按角色守卫（BOSS/DEPT_MANAGER/OPERATOR/SUPER_ADMIN 分级）。
- **依据**：02-红线（安全规则）、01-角色权限模型、08-矩阵（员工/运营能力）。

### B2【P0】高危写接口无归属/角色校验（在 B1 之上放大）
- **位置**：`order/controller/OrderController.updateStatus`（L96-102）→ `order/service/OrderService.updateStatus`（L197-249）：**任意调用者可把任意工单流转为 DEAL（成交）并自动触发 `rewardService.settleForOrder`**，涉及金额/奖励；`approval/controller/ApprovalController.productAudit/downloadAudit`（L67-127）：任意调用者可审核通过/驳回（含生成 24h 限时无水印下载链接）；`blacklist/controller/BlacklistController.add/release`（L54-77）：任意调用者可加/解黑名单。
- **期望**：写操作强制 STAFF + 角色/归属校验；成交流转仅工单归属顾问或管理角色；审核仅 OPERATOR/SUPER_ADMIN/BOSS。
- **依据**：02-红线、方案纪要（审批人=运营/超管/老板）。

### B3【P1】工单详情越权 + 内部字段泄露
- **位置**：`order/controller/OrderController.detail`（L83-86，**无 user 参数/无归属校验**）→ `OrderService.detail`（L175-188）返回 `internalRemark`（内部备注）与 `createdBy`。
- **影响**：任何调用者可看任意工单详情含**内部备注**；与分页接口（有 mineOnly/isManager 裁剪）语义不一致。
- **期望**：detail 同样按角色裁剪：顾问仅自己的单（或团队），管理角色全量；内部备注仅管理角色可见。
- **依据**：C7（员工四维/归属语义）。

### B4【P1】管理角色判定遗漏运营/超管
- **位置**：`order/service/OrderService.isManager`（L254-256）仅认 `BOSS/DEPT_MANAGER`；`lead/service/LeadService.page` 类似（roleCode 判定待核）。
- **影响**：OPERATOR / SUPER_ADMIN 在工单分页被强制 `owner=自己`，无法全量查看/管理（与「运营=配置/审批/审计」定位冲突）。
- **期望**：管理角色集合含 OPERATOR/SUPER_ADMIN（按 01 角色模型）。

### B5【P2】敏感授权模块的匿名兜底
- **位置**：`sensitive/controller/SensitiveViewController.applyView/quota`（L38-56）：`user == null ? null : ...`，未登录也可进（Service 内部可能有校验，但 Controller 未拒）。
- **期望**：统一在 B1 的全局鉴权下收敛。

---

## 三、数据一致性（红线核查 —— 全部通过 ✅）

| 红线 | 核查结果 |
|------|----------|
| 业务 ID：小写前缀 + 32 位随机 | ✅ `BizIdGenerator.generate(...)` 使用前缀：client(3)/submit/rule/reward/report/papr/order/noti/lead/dldapr/ch/alloc，全部小写规范；未发现用自增主键做业务 ID 外泄 |
| XXL-Job，禁 @Scheduled | ✅ 全库无 `@Scheduled`；定时任务均为 `@XxlJob`：`partnerProductExpireJob`（partner/job）、`leadRecycleJob`、`leadRecycleWarnJob`（lead/job）+ `XxlJobConfig` 注册 |
| 敏感字段 AES + 摘要 | ✅ 实体：ClientProfile/PersonalProfile/Lead 等 `@TableField(typeHandler=AesTypeHandler)`；**读取路径手动解密**：MiniAuthService/OrgService/OrgWriteService/LeadService/BlacklistService/SensitiveViewService/SmsAdminService/AuthService 均 `AesUtils.decrypt`（符合红线「typeHandler 仅写入生效，读取需 Service 手动做」）|
| 精确匹配用摘要 | ✅ `HashUtils.sha256Hex` 用于 phone/creditCode 等值比对（auth/client/lead/order/report/reward/staff/mini）|
| 操作日志 | ✅ `@OpLog` 切面（OperationLogAspect）落 `t_operation_log`，参数快照自动脱敏手机号/身份证，日志失败不影响主流程；已用于 order/approval 写操作 |
| 事务边界 | ✅ 关键写路径均有 `@Transactional(rollbackFor=Exception.class)`：OrderService.create/updateStatus、RewardService.settleForOrder、MiniProductService.save/applyDelete/auditDelete、MiniClientService.claim 等 |
| 排序字段防注入 | ✅ OrderService/RewardService 均用 `ORDER_FIELDS` 白名单 + `PageOrder.apply` |

---

## 四、契约对齐（前端 loan-mini ↔ 后端）

| 项 | 结论 |
|----|------|
| mini 端 auth 登录 | ✅ `POST /api/mini/auth/login` 前后端一致（KB 04 写 wx-login 属文档过期，阶段一已提）|
| match/run clientCode | ✅ 已修：客户强制登录态、STAFF 读取 body.clientCode（防越权替客）|
| 员工 report/order 详情 | ✅ 已修：STAFF 传 null 跳过客户归属校验（阶段一 P0 修复）|
| 产品 CRUD 权限 | ⚠️ 后端 `MiniProductController` 全部 `requireChannel`（仅渠道），KB 04 记载 CHANNEL/STAFF → 契约以代码为准（阶段一 A8 已提）|
| 分配审批 3 接口 | ✅ 后端就绪（MiniClientController allocation-approvals），前端待补（阶段一 M6）|
| 产品删除终审 | ✅ 后端 `/api/mini/partner-product/delete/*` 就绪；KB 04 路径记载错误（阶段一 A6 已提）|
| admin 端契约 | ⚠️ 无法与 loan-web 前端核对（红线「不碰 loan-web」），但 admin 接口无守卫（B1）为独立风险 |

---

## 五、明显缺陷

| # | 问题 | 位置 | 级别 |
|---|------|------|------|
| B6 | **删除终审「物理删除」未真正清理合作库**：`auditDelete` 批准分支只 `approvalMapper.deleteById`，注释 TODO 同步清理 `t_partner_product` 并写审计 → 批准删除后产品仍留在合作库，数据不一致 | `mini/service/MiniProductService.auditDelete` L259-262 | P1 |
| B7 | 产品审批「通过」后未校验/落 `t_partner_product` 上架记录（C9 状态机 OK 与真实上架库的联动缺） | `mini/service/MiniProductService` | P1 |
| B8 | 奖励结算幂等仅依赖工单状态，建议叠加 `rewardSettledFlag` 双重校验（防并发/重放） | `reward/service/RewardService.settleForOrder` L80 | P2 |
| B9 | 通知缺失：产品删除申请/审批结果/分配审批均无站内信+短信（TODO） | MiniProductService L267/L344 | P2 |
| B10 | 工单服务类型未接真实产品分类（TODO，`resolveServiceType` 硬编码返回「融资」） | `mini/service/MiniOrderService` L160-167 | P2 |
| B11 | 渠道 V2 未落地：渠道应从客户档案的邀请/渠道绑定关系推导（TODO） | `screening/service/ScreeningService` L113 | P2 |
| B12 | `plan/` 27 文件为清单外新增域，无测试覆盖证据；且**无守卫**（并入 B1） | plan/ | P1 |
| B13 | 配置仅 `application.properties` 单文件：`wechat.mock=true` 全局生效（无 prod profile）→ 生产同样 mock 登录 | application.properties L63 | P1（阶段二 H2 已提，此处后端复述） |
| B14 | 8 个空占位包（vip/stats/oss/match/job/interceptor/enums/crm）与验收清单不符观感，需在知识库标注"占位" | 各空目录 | P2 |
| B15 | 匿名可读 `/api/mini/partner-product/active`（在售产品数量）—— 属公开数据可接受，但建议明确 | partner/PartnerProductController L126 | P2 |

---

## 六、问题清单（汇总）

| # | 级别 | 问题 | 位置 |
|---|------|------|------|
| B1 | **P0** | admin 端整体无鉴权/无角色权限（anyRequest().permitAll + admin 控制器无守卫 + @CurrentUser 注入 null 不拒绝）→ 匿名/客户/渠道可调管理接口 | SecurityConfig、JwtAuthenticationFilter、CurrentUserAspect、全部 admin Controller |
| B2 | **P0** | 高危写接口无归属/角色校验：工单流转成交（触发奖励结算）、产品审核、下载审批、黑名单增删 | OrderController/Service、ApprovalController、BlacklistController、RewardController |
| B3 | **P1** | 工单详情越权 + 内部备注 internalRemark 泄露（detail 无归属校验） | OrderController.detail、OrderService.detail |
| B4 | **P1** | 管理角色判定漏 OPERATOR/SUPER_ADMIN（被限 own） | OrderService.isManager 等 |
| B6 | **P1** | 删除终审未真正清理 t_partner_product（批准删除后产品残留） | MiniProductService.auditDelete |
| B7 | **P1** | 产品审批通过与 t_partner_product 上架联动缺 | MiniProductService |
| B12 | **P1** | plan/ 新增域（27 文件）无守卫 + 清单外（并入 B1） | plan/ |
| B13 | **P1** | wechat.mock=true 无环境区分（生产 mock） | application.properties |
| B5 | **P2** | 敏感授权 controller 匿名兜底 | SensitiveViewController |
| B8 | **P2** | 奖励结算幂等建议叠 rewardSettledFlag | RewardService.settleForOrder |
| B9 | **P2** | 通知缺失（删除/审批结果站内信+短信 TODO） | MiniProductService |
| B10 | **P2** | 工单服务类型硬编码「融资」（TODO 接 t_bank_product） | MiniOrderService |
| B11 | **P2** | 渠道绑定关系推导未落地（TODO 渠道V2） | ScreeningService |
| B14 | **P2** | 8 个空占位包与验收清单观感不符，需标注 | 空目录 |
| B15 | **P2** | /api/mini/partner-product/active 匿名可读（公开数据可接受） | PartnerProductController |

### 通过项（合规确认 ✅）
- ✅ 无 @Scheduled，定时任务全走 XXL-Job（partnerProductExpireJob / leadRecycleJob / leadRecycleWarnJob）
- ✅ BizIdGenerator 前缀全部小写规范（client/submit/rule/reward/report/papr/order/noti/lead/dldapr/ch/alloc）
- ✅ 敏感字段：AES typeHandler 写入 + Service 手动解密读取 + SHA-256 摘要等值比对（各读取路径均已覆盖）
- ✅ 操作日志：@OpLog 切面落 t_operation_log + 参数脱敏
- ✅ 事务边界：关键写路径均有 @Transactional
- ✅ 排序白名单防注入（PageOrder + ORDER_FIELDS）
- ✅ mini 端契约：阶段一 P0 修复（clientCode 透传、员工详情 403）已生效

---

## 七、结论与优先级建议

1. **上线前必须修复（P0）**：B1/B2 —— 管理端鉴权与角色权限是当前后端**最大安全缺口**，任何形式上线前必须补（SecurityConfig 收紧 + admin 角色守卫 + 高危写接口归属/角色校验）。建议落地 apiperm 的运行时拦截（表/接口已存在）。
2. **上线前建议修复（P1）**：B3（工单详情越权/内部备注泄露）、B4（运营/超管全量）、B6/B7（产品删除/上架与 t_partner_product 一致性）、B13（配置分环境）。
3. **后续打磨（P2）**：B8-B11、B14、B15。
4. **文档回写**：阶段二验收清单已明显过时（order/approval/reward/blacklist/dashboard/plan 已有实现），建议 QA 阶段回写知识库与验收清单；`plan/` 域与用户对齐命名后补测试。

> 本清单仅审查输出，未修改任何代码（工程阶段处理）。
