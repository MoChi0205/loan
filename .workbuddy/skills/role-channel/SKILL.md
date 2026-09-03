---
name: role-channel
description: >-
  loan-main 业务角色「渠道合作方」的功能边界、数据范围与禁止项（沙箱隔离核心角色）。
  涉及渠道角色的需求评审、小程序页面改动、接口改动、产品与线索录入、tabBar 改动、验收走查时使用；
  使用前必须先执行 loan-knowledge 的 Step 0（查 10-历史结论与决策日志）。
---

# 业务角色：渠道合作方（role=channel）— 沙箱隔离角色

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "渠道\|CHANNEL\|沙箱\|线索\|lead" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
   > ⚠️ 渠道是**沙箱隔离**角色，任何「给渠道开个口子」的需求都必须先与用户确认，**不得自行判断放宽**。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `channel` |
| `userType` | `CHANNEL` |
| `roleCode` | 空（渠道无 roleCode） |
| 后端推导 | `MiniAuthController.resolveRoleInfo()`：`LoanUser.TYPE_CHANNEL.equals(userType)` → `role = "channel"` |
| 前端推导 | `store/user.js resolveRole()`：`type === 'CHANNEL'` → `channel` |
| getter | `store.isChannel` = `role === 'channel'` |
| 持久化 | `store.role` + storage key `loan_role` |
| 展示名 | `roleLabel` = `渠道合作方` |
| **注意** | `isStaff` **不包含** channel（`['adviser','deptmgr','boss','operator','super']`）→ 渠道**不是**员工，走独立分支 |

## 2. 功能边界（能做什么）

- 登录 / 绑定（`POST /api/mini/auth/login`）
- **我的产品（自有录入）**：`GET /api/mini/product/list`（后端 `requireChannel`）、`POST /api/mini/product`（录入 DRAFT）、
  `GET/PUT /api/mini/product/{code}`、`/submit`、`/revoke`、`/delete-apply`、`/delete-cancel`
  —— **删除走审批**（复用 `t_product_approval`，`applyType=CREATE/DELETE`，C9，**不新增表**）
- **录入客户（线索）**：`POST /api/mini/lead/submit` —— 走 **`Lead` 不进 `ClientProfile`**，
  归属 = **公海**（`owner_staff_id = NULL`），由公司员工认领（C20）
- **我录入的线索**：`GET /api/mini/lead/my`（**仅本人录入**）
- **新增后立即可见**：`PENDING_APPROVAL / NEW / REJECTED` 等全部本人线索都进入“我的线索”；审批只控制是否进入公司公海，不影响录入渠道本人查看
- **Web 本人客户档案（只读）**：`GET /api/channel/client/page|/{clientCode}`；仅本人录入且已通过终审、已形成客户档案的数据，展示归属顾问姓名
- **Web 本人客户分析报告（只读）**：`GET /api/channel/report/page|/{reportNo}`；仅上述本人客户的分析报告，不含命中产品明细
- **批量只读查询**：`POST /api/channel/client/batch`、`POST /api/channel/report/batch`；业务编码去重、单次最多 100 条、一次查库，越权/未命中项不返回
- **渠道新增线索终审**：新增线索初始 `PENDING_APPROVAL`；由 BOSS / SUPER_ADMIN / SUPER 任一单级终审，通过后 `NEW` 进入公海，驳回为 `REJECTED`
- **我的账户 = 银行信息**（C8）：所属银行 / 合作开始 / 银行联系人 / 银行编码
- 材料上传（`POST /api/mini/upload`）

## 3. 禁止项（不能做什么）— 6 条（沙箱隔离核心）

1. ❌ **不可操作智能匹配 —— 渠道是唯一不可操作匹配的角色**（C1）
   —— 后端 `MiniMatchController` 在 `run`(L61)、`history`(L135)、`reportList`(L135)、`products`(L162)、
   `diagnosis`(L185)、`detail`(L203) **六处均有 `TYPE_CHANNEL` 守卫**
2. ❌ **小程序不可见报告；Web 不可见非本人录入客户报告** —— C3 / D50；小程序 `reportList` 渠道分支仍返回空，Web 仅走 `/api/channel/report/**` 本人范围
3. ❌ **不可见服务单 tab** —— `TabBar.vue` 渠道分支无 `order`；08 矩阵「❌ 隐藏」
4. ❌ **小程序不可见客户档案；Web 客户档案不可写、不可查重、不可归属流转** —— C2 / C10 / D50；Web 只读本人录入形成的档案
5. ❌ **录入命中唯一索引冲突时，只返友好文案，严禁泄露归属人** —— C20（`msg="该客户已被录入，请联系运营"`）
6. ❌ **看不到匹配结果、看不到报告命中产品明细，不可修改客户归属**；D50 仅允许 Web 只读显示归属顾问姓名，不返回归属工号

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 产品 | **仅自有**（`requireChannel` + 本人） |
| 线索 | **仅本人录入**的 `t_lead`（待审/通过/驳回全部可见）；其他渠道不可见 |
| 报告 | 小程序不可见；Web 仅本人录入且已形成客户的分析报告只读 |
| 匹配 / 命中产品明细 / 工单 | 一律不可见（空集或拒绝） |
| 客户档案 | 小程序不可见；Web 仅本人录入且已形成客户的档案只读 |
| 归属信息 | Web 仅显示顾问姓名，不返回工号；冲突提示仍不得泄露归属人 |

> 沙箱语义：渠道只能看到本人产出的线索、产品，以及这些线索形成的客户/报告只读视图；客户服务归属仍是公司侧资产，仅展示顾问姓名，不开放任何写操作。

## 5. tabBar 结构

**4 tab**：首页 · 我的产品 · 录入客户 · 我的

依据 `loan-mini/components/TabBar.vue:47-54`（`store.isChannel` 分支）：
`home(/pages/home/home)` · `product(/pages/product/list)` · `client(/pages/client/create)` · `mine(/pages/mine/mine)`

> ⚠️ **已知文档错误**：`docs/knowledge-base/01-角色权限模型.md` 的「角色化导航（C17）」写的是 **3 tab**（首页·我的产品·我的），
> **与代码不符**。已记入 **D13**，**以 `TabBar.vue` 的 4 tab 为准**，`01` 文档待修正。
> 自绘 TabBar（C17），`pages.json` 已无原生 tabBar；切换一律 `uni.reLaunch`，禁用 `uni.switchTab`。

## 6. 相关接口清单

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `POST /api/mini/auth/login` | 登录 | 公开 |
| `GET /api/mini/me` | 档案摘要（含 `roleInfo`） | 已登录 |
| `GET /api/mini/product/list` | 我的产品 | **仅 CHANNEL**（后端 `requireChannel`） |
| `POST /api/mini/product` | 录入产品（DRAFT） | **仅 CHANNEL**（KB 旧文「CHANNEL/STAFF」不实） |
| `GET /api/mini/product/{code}` | 产品详情 | 仅本人 |
| `PUT /api/mini/product/{code}` | 编辑产品 | 仅本人 |
| `POST /api/mini/product/{code}/submit` | 提交审批 | 仅本人 |
| `POST /api/mini/product/{code}/revoke` | 撤销审批 | 仅本人（PENDING） |
| `POST /api/mini/product/{code}/delete-apply` | 申请删除 | 仅本人（OK 状态） |
| `POST /api/mini/product/{code}/delete-cancel` | 撤销删除 | 仅本人（PENDING_DELETE） |
| `POST /api/mini/lead/submit` | 录入线索（先待公司审批） | **含 CHANNEL**（沙箱隔离） |
| `GET /api/mini/lead/my` | 我录入的全部线索，新增后立即可见 | CHANNEL 仅本人（含待审/通过/驳回） |
| `POST /api/channel/lead` | Web 录入线索，初始待终审 | CHANNEL 本人 |
| `GET /api/channel/lead/page` | Web 本人录入线索分页 | CHANNEL 本人 |
| `GET /api/channel/client/page` / `/{clientCode}` | Web 本人客户分页/详情 | CHANNEL 本人只读 |
| `POST /api/channel/client/batch` | Web 本人客户批量摘要（最多 100） | CHANNEL 本人只读 |
| `GET /api/channel/report/page` / `/{reportNo}` | Web 本人客户分析报告分页/详情 | CHANNEL 本人只读 |
| `POST /api/channel/report/batch` | Web 本人客户报告批量摘要（最多 100） | CHANNEL 本人只读 |
| `POST /api/mini/upload` | 材料上传 | CUSTOMER / STAFF / CHANNEL |

> 待删产品终审在 `GET /api/mini/partner-product/delete/pending` +
> `POST /api/mini/partner-product/delete/{approvalNo}/audit`，权限是**运营 / 超管**，**不是渠道**。

## 7. 相关结论编号

`C1`（渠道唯一不可操作匹配）· `C3`（小程序不可见报告，Web 由 D50 覆盖）· `C8`（我的页账户 = 银行信息）· `C9`（渠道产品删除走审批，不新增表）· `C20`（渠道线索录入与唯一索引冲突不泄归属人）· `D50`（Web 本人客户/报告/归属姓名只读 + 渠道内容终审）

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**；D13 = 渠道 tabBar 4 tab 纠错）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（渠道列）
- `docs/knowledge-base/04-后端 API 契约.md#线索录入（融资需求，T4 · 渠道走 Lead）`
- `docs/knowledge-base/04-后端 API 契约.md#工单 / 产品`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#关键业务表`（`t_lead` / `t_lead_ent_ext`）
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/knowledge-base/05-前端工程要点.md#自绘 TabBar（C17，禁用原生）`
- 代码真源：`loan-mini/components/TabBar.vue`、`loan-service/src/main/java/com/loan/mini/controller/MiniLeadController.java`、`MiniProductController.java`

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] Web 客户/报告是否严格走 `/api/channel/**` 并按本人 `userNo` 隔离？小程序报告 tab 是否仍隐藏？
- [ ] 新增接口是否在 `MiniMatchController` 六处守卫之外**也**加了 `TYPE_CHANNEL` 守卫？
- [ ] 唯一索引冲突文案是否只返友好提示（未泄露归属人）？
- [ ] tabBar 是否以 `TabBar.vue` 的 **4 tab** 为准（未沿用 01 文档的 3 tab 错误）？
- [ ] 渠道录入是否走 `Lead` 并先 `PENDING_APPROVAL`，终审通过才进公海（未直接落 `ClientProfile`）？
- [ ] 新增后是否立即出现在渠道本人列表，且待审/通过/驳回均可见、其他渠道不可见？
- [ ] 是否只返回归属顾问姓名，未暴露归属工号、匹配 trace 或命中产品明细？
