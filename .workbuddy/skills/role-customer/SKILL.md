---
name: role-customer
description: >-
  loan-main 业务角色「客户」的功能边界、数据范围与禁止项。涉及客户角色的需求评审、小程序页面改动、
  接口改动、权限/菜单/tabBar 改动、报告与工单可见性核对、验收走查时使用；
  使用前必须先执行 loan-knowledge 的 Step 0（查 10-历史结论与决策日志）。
---

# 业务角色：客户（role=customer）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "客户\|CUSTOMER\|匹配\|报告\|脱敏" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（角色边界属安全域，不得自行放宽）。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `customer` |
| `userType` | `CUSTOMER` |
| `roleCode` | 空（客户无 roleCode） |
| 后端推导 | `MiniAuthController.resolveRoleInfo()`：`user == null` 或 `userType == null` 时**兜底 customer**；`TYPE_CUSTOMER` → `role = "customer"` |
| 前端推导 | `store/user.js resolveRole()`：`!user \|\| !user.userType` → `customer`；`type === 'CUSTOMER'` → `customer`；**未知 userType 也归 customer（最小权限兜底）** |
| 持久化 | `store.role` + storage key `loan_role`（`/api/mini/me` 返回 `roleInfo` 时以服务端为准刷新） |
| 展示名 | `roleLabel` = `客户` |

> ⚠️ **兜底特性**：customer 是**全局最小权限兜底角色**。任何无法识别的用户（未登录、`userType` 为空、非 CUSTOMER/CHANNEL/STAFF）都会被判为 customer。
> 因此**不得**因为"某个功能对 customer 可见"就推断它对所有人可见。

## 2. 功能边界（能做什么）

- 登录 / 邀请码绑定（`POST /api/mini/auth/login`，`inviteCode` 参数；`auth/login-bind` 已废弃）
- **身份认证**（企业 / 个人，`POST /api/mini/auth/enterprise`）—— **仅客户有此能力**（08 矩阵：其余 6 角色为「—」）
- **智能匹配（仅自己）**：`POST /api/mini/match/run`，**不传 `clientCode`**，用登录态
- **我的报告（仅按日期筛选自己的）**：`GET /api/mini/report/list` → 后端走 `myReportsByDate(userNo, page, size, dateRange)`
- **经营诊断**：`GET /api/mini/report/{reportNo}/diagnosis`（**客户校验归属**）
- **服务单（自己的）**：`GET /api/mini/order/list`（客户仅「状态 + 时间」二维筛选，C7）
- **我的账户 / 档案**（C8）：实名状态 / 绑定手机号 / 性别 / 注册时间 / 邀请人（**无客户编号**）
- **奖励汇总**（08 矩阵：仅客户 ✅，其余为「—」）
- 材料上传（`POST /api/mini/upload`，CUSTOMER/STAFF/CHANNEL 均可）

## 3. 禁止项（不能做什么）— 6 条

1. ❌ **看不到命中产品明细**（`bankName` / `productName` 对客脱敏）—— C4；`MiniMatchController.reportProducts` 仅 STAFF 可见；`04#通用约定`「产品名/银行名对客脱敏（仅员工可见）」
2. ❌ **不能看他人报告 / 工单** —— `report/list` 客户分支强制 `user.getUserNo()`，`order` 客户校验归属；C3 / C7
3. ❌ **不能用员工四维筛选**（客户姓名 / 信用代码 / 归属 / 时间组合）—— `report/list` 客户分支**忽略所有跨用户检索参数**（`query` / `credit` / `owner`），**只透传 `dateRange`**；C3 / C11
4. ❌ **无审批中心入口** —— `mine.vue` `isApproverRole = ['boss','operator','super']` 不含 customer；08 矩阵 ❌
5. ❌ **不能录入产品、不能查重 / 归属流转** —— `POST /api/mini/product` 后端 `requireChannel`（仅渠道）；`GET /api/mini/client/search` 仅 STAFF
6. ❌ **不能进客户管理 / 线索公海** —— 08 矩阵对**全部 7 角色**均为 ❌，规划在 Web 端阶段二

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 标识 | `own`（`clientCode == user.userNo`） |
| 报告 | **仅自己**，且只支持**日期**维度筛选（`myReportsByDate`） |
| 报告详情 | 对客只返回日期维度；命中产品脱敏，仅展示数量 |
| 工单 | 仅自己的（`myOrdersByFilter`，状态 + 时间） |
| 产品 | 不可见 |
| 线索 | 不可见（`clientCode == VIP` 派生仅用于 `lead/submit` 的 source，不产生可见列表） |

## 5. tabBar 结构

**5 tab**：首页 · 智能匹配 · 我的报告 · 服务单 · 我的

依据 `loan-mini/components/TabBar.vue:55-61`（非渠道分支）：
`home(/pages/home/home)` · `match(/pages/match/match)` · `report(/pages/report/list)` · `order(/pages/order/list)` · `mine(/pages/mine/mine)`

> 自绘 TabBar（C17），`pages.json` **已无原生 tabBar 配置**；切换一律 `uni.reLaunch`，**禁用 `uni.switchTab`**。

## 6. 相关接口清单

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `POST /api/mini/auth/login` | 登录（含 `inviteCode` 绑定） | 公开 |
| `GET /api/mini/me` | 档案摘要（含 `roleInfo`） | 已登录 |
| `POST /api/mini/auth/enterprise` | 企业 / 个人认证 | 已登录（CUSTOMER） |
| `POST /api/mini/match/run` | 智能匹配（**不传 clientCode**） | CUSTOMER / STAFF；**CHANNEL 禁入** |
| `GET /api/mini/match/history` | 匹配历史 | 已登录 |
| `GET /api/mini/report/list` | 我的报告（**仅日期维度**） | 客户强制 own |
| `GET /api/mini/report/{reportNo}` | 报告详情 | 客户校验归属 |
| `GET /api/mini/report/{reportNo}/diagnosis` | 经营诊断 | 客户校验归属 |
| `GET /api/mini/order/list` | 服务单（状态 + 时间） | 客户 own |
| `GET /api/mini/order/{orderNo}` | 工单详情 | 客户校验归属 |
| `POST /api/mini/upload` | 材料上传 | CUSTOMER / STAFF / CHANNEL |

## 7. 相关结论编号

`C1`（匹配资格，客户可操作自己）· `C3`（报告查询维度角色二分）· `C4`（命中产品对客脱敏）· `C7`（服务单筛选维度）· `C8`（我的页账户字段）· `C17`（角色化导航自绘 TabBar）· `C21`（OCR 回灌诊断）

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/01-角色权限模型.md#前端：Pinia store（store/user.js）`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（文件首表）
- `docs/knowledge-base/04-后端 API 契约.md#关键接口索引`
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`（脱敏规则）
- `docs/knowledge-base/04-后端 API 契约.md#字段命名红线`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/knowledge-base/05-前端工程要点.md#自绘 TabBar（C17，禁用原生）`
- 代码真源：`loan-service/src/main/java/com/loan/mini/controller/MiniMatchController.java`（`reportList` 三分支）、`loan-mini/store/user.js`

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 是否误把「对 customer 可见」当成「对所有人可见」（customer 是最小权限兜底）？
- [ ] 新增客户可见字段是否触发脱敏（产品名 / 银行名 / 手机号）？
- [ ] 是否绕过 `report/list` 的 own 强制（客户不得跨用户检索）？
- [ ] tabBar 改动是否以 `TabBar.vue` 为准（不采信 01 文档的旧描述）？
