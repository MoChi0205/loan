# 04 · 后端 API 契约（小程序端 /api/mini/*）

> **最新校正（2026-08-31）**：据 `scripts/check-kb-consistency.py` 检查 5 全量复核，修正 3 条管理端审批路径（实际为 `unified/*`，旧文 `allocation/{approvalNo}/audit`、`/approval/counts`、`/approval/pending` 在代码中不存在）；统一 2 处占位符命名（`{code}` → 代码实际 `@PathVariable` 名 `{clientCode}`）；补登记 8 个代码已有而文档遗漏的 `/api/mini/**` 接口（`auth/personal`、`match/history`、`partner-product/active`、`invitation/{bind,mine,records}`、`reward/{mine,mine/summary}`）。**契约以 `loan-service` 源码为准。**
> **2026-09-02 联调补充**：小程序匹配的 `applyCity` 改为前后端共同必填；新微信客户响应 `CREATED_UNASSIGNED` 并进入未分配池；审批、工单、策略、奖励分页批量补齐姓名/产品名；附件分页支持 `keyword`；策略模版分页支持 `status`；策略提供按执行计划编码精确判断引用接口。
> **2026-08-30 校正**：据 `loan-service` 全部 `com.loan.mini.controller.*` 源码逐接口复核，修正以下陈旧项——① `match/report/{reportNo}/products|diagnosis` → 实际归属 `MiniMatchController` 的 `report/{reportNo}/products|diagnosis`；② 产品操作路径 `{no}` → `{code}`；③ 分配审批 `{no}` → `{approvalNo}`；④ 工单/报告 `{no}` → `{orderNo}/{reportNo}`；⑤ `auth/login-bind` 已废弃（合并进 `auth/login.inviteCode`）；补 `auth/enterprise`、`wecom/qrcode`、`client`(录入)。以本文 + 源码为准。

## 通用约定

- Base URL：`loan-service/api/mini/*`（小程序端）
- 管理端：`/api/admin/**` 主要供 STAFF；渠道 Web 使用 `/api/channel/**` 专用工作区接口，网关按 `channel:*` 类型规则放行并由后端强制本人数据范围
- 返回：`Result<T>`（code=0 成功，code≠0 失败）+ message
- 鉴权：`@CurrentUser LoanUser`（前端 Token 自动注入，拦截器读取 `Authorization`）
- 分页：`PageResult<T>`（page/size/total/records）
- 字段命名：camelCase JSON（与 Java DTO 一致）
- 敏感字段：手机号返回掩码（`138****0001`）；产品名/银行名对客脱敏（仅员工可见）

## 关键接口索引

### 鉴权 / 用户
| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/auth/login` | **实际路径**（微信登录，兼容 phone；历史文档写 wx-login 已过时） | 公开 |
| `GET /api/mini/me` | 当前用户档案摘要（含 `roleInfo`、`ownerStaffName`、`referrerName`；顾问归属与分享引荐分开返回） | 已登录 |
| ~~`POST /api/mini/auth/login-bind`~~ ⚠️ 已废弃 | 合并进 `POST /api/mini/auth/login` 的 `inviteCode` 参数（2026-08-30 校正，旧方案文档仍写 login-bind 不实） | — |
| `POST /api/mini/auth/enterprise` | 企业/个人认证（CUSTOMER） | 已登录(CUSTOMER) |
| `POST /api/mini/auth/personal` | **个人实名认证**（Mock 三要素校验 + 落库留痕，`PersonalController`；未登录抛 `UNAUTHORIZED`） | 已登录 |
| `GET /api/mini/wecom/qrcode` | 企微客服活码 URL | 公开 |

### 匹配（C15）
| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/match/run` | 发起匹配；body 中 `applyCity` **必填**（市一级名称，用于服务城市精确筛选）；可传 `clientCode`（**C2 替客匹配必传目标客户**；客户不传用登录态）。员工替客匹配目标客户已归属他人且非本人且无已通过归属审批（`hasApprovedOwnership`）→ 后端 FORBIDDEN，前端引导至「客户分配」发起归属审批 | CUSTOMER / STAFF；CHANNEL **禁入（后端已守卫）** |
| `GET /api/mini/match/history?page&size` | 我的匹配历史（`PageResult<Map>`） | 客户本人（后端 `requireClient` 兜底） |
| `GET /api/mini/report/{reportNo}/products` | 报告命中产品明细（MiniMatchController，非 match/report） | **仅 STAFF**（C4 对客脱敏） |
| `GET /api/mini/report/{reportNo}/diagnosis` | 报告经营诊断 | 客户校验归属 / 员工全量 |

> **`materialVersion`（T2）**：diagnosis 响应可含 `materialVersion` 字段，由 `data_json._ocrMeta.version` 派生，无则默认 `v1`。**OCR 回灌仅补空、不覆盖既有值**（`SubmissionFactsMerger.mergeFromOcr` 只填缺失字段）。

### 报告
| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/report/list` | 我的报告 | 角色二分：CUSTOMER 仅日期 / STAFF 四维 + 跨归属 |
| `GET /api/mini/report/{reportNo}` | 报告详情 | **客户校验归属 / 员工全量（传 null 跳过）** |

### 客户查重 + 归属流转（C2/C10/C19）
| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/client/search?keyword=...` | 查重（≥2 字） | 仅 STAFF；CHANNEL **禁止** |
| `POST /api/mini/client` | 录入新客户；新客户不自动归属，响应 `action=CREATED_UNASSIGNED` 并进入未分配池 | 仅 STAFF |
| `POST /api/mini/client/{clientCode}/claim` | 顾问申请认领：已归属本人幂等通过；未归属或已归属他人提交审批，不直接转移 | 仅 ADVISER |
| `GET /api/mini/client/{clientCode}/claim-status` | 分配审批状态轮询 | 仅 STAFF |
| ~~`GET /api/mini/client/allocation-approvals/pending`~~ ⚠️ 已废弃（兼容期） | 待审列表 → 改用 `GET /api/mini/approval/pending?type=ALLOCATION` | STAFF + OPERATOR/SUPER/BOSS |
| ~~`POST /api/mini/client/allocation-approvals/{approvalNo}/approve`~~ ⚠️ 已废弃（兼容期） | 通过 → 改用 `POST /api/mini/approval/ALLOCATION/{approvalNo}/audit` | 同上 |
| ~~`POST /api/mini/client/allocation-approvals/{approvalNo}/reject`~~ ⚠️ 已废弃（兼容期） | 驳回 → 改用 `POST /api/mini/approval/ALLOCATION/{approvalNo}/audit` | 同上 |

### 管理端未分配客户池
| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/admin/client/unassigned/page?keyword&page&size` | 分页查询 `ownerStaffCode` 为空的客户；批量补齐待审顾问姓名，页面不展示业务编码 | STAFF（不含渠道） |
| `POST /api/admin/client/{clientCode}/claim` | 顾问按客户业务编码申请认领，目标顾问取当前登录人 | ADVISER |
| `POST /api/admin/client/{clientCode}/assign` | **直接落归属、无需审批**（D39/C23）：覆盖式 `assignOwnerIfUnchanged`，可重分配已归属客户；目标=ADVISER/DEPT_MANAGER（`ASSIGNABLE_ROLES`）；body 兼容 `{targetStaffCode}`（新）与 `{adviserStaffCode}`（旧） | DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER（前端操作前弹窗二次确认） |
| `POST /api/admin/client/{clientCode}/recycle` | 管理端手动回收进公海（C26）：清空归属+置冷却，覆盖冷却期，不删档案；仅 `LambdaUpdateWrapper` 更新非加密字段规避 AES 二次加密 | MiniRoleGuard.requireApprover（DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER） |

> 两个写入口语义已分化：`/assign` 走 `ClientAllocationService.directAssign` **立即落归属、不生成审批单**（管理端指定场景）；`/claim`（顾问认领未分配客户）走 `ClientAllocationService.apply` 生成 `ALLOCATION` 待审单，仍由管理角色审批（DM 仅本团队，跨团队 BOSS，见 C24）。两者均用条件更新防并发覆盖。超期自动回收由 XXL-Job `clientRecycleJob`/`clientRecycleWarnJob` 调度（配置 `t_client_recycle_config`）。

### 线索录入（融资需求，T4 · 渠道走 Lead）

| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/lead/submit` | 录入融资需求线索；body 新增企业字段 `entName`/`creditCode`/`industry`/`foundYears`/`annualTaxAmount`/`annualInvoiceAmount`（**全部可选、前端字符串**）；`source` 由后端按用户类型派生：**CHANNEL→PENDING_APPROVAL（终审通过才进入公海） / BOSS→BOSS / 其他 STAFF→ADVISER / CUSTOMER→VIP**（旧 `MINI` 字典已废弃）；响应由 `Result<String>` 改 `Result<Map>`：`{leadNo, duplicated}`，重复时 `msg="该客户已被录入，请联系运营"` 且**不泄归属人** | **含 CHANNEL**（沙箱隔离） |
| `GET /api/mini/lead/my?page&size` | **我录入的线索**；`PageResult<Map>`，字段 `leadNo`/`contactName`(脱敏)/`entName`/`phone`(掩码)/`followStatus`/`createdAt`，渠道按稳定 `userNo` 强隔离且不按审批状态过滤，新增后立即可见（含 `PENDING_APPROVAL/NEW/REJECTED`） | 已登录，仅本人 |

### 渠道 Web 工作区（D50）

| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/channel/lead` | 渠道录入线索；服务端强制来源、录入主体和 `PENDING_APPROVAL` | CHANNEL 本人 |
| `GET /api/channel/lead/page` | 本人录入线索组合分页，不按归属顾问或审批状态隐式过滤；新增后立即可见，其他渠道不可见 | CHANNEL 本人 |
| `GET /api/channel/client/page` | 本人录入并已转化客户分页，返回脱敏手机号与 `ownerStaffName` | CHANNEL 本人只读 |
| `POST /api/channel/client/batch` | body `{codes:[clientCode...]}`；去空、去重、保持请求顺序，单次最多 100 条，越权/未命中项不返回 | CHANNEL 本人只读 |
| `GET /api/channel/client/{clientCode}` | 客户档案详情；越权编码返回 FORBIDDEN | CHANNEL 本人只读 |
| `GET /api/channel/report/page` | 本人录入客户的分析报告组合分页 | CHANNEL 本人只读 |
| `POST /api/channel/report/batch` | body `{codes:[reportNo...]}`；去空、去重、保持请求顺序，单次最多 100 条，越权/未命中项不返回 | CHANNEL 本人只读 |
| `GET /api/channel/report/{reportNo}` | 分析报告详情与归属顾问姓名；不返回命中产品明细 | CHANNEL 本人只读 |
| `GET /api/admin/approval/channel-lead/page` | 渠道新增线索待审分页 | BOSS/SUPER_ADMIN/SUPER |
| `POST /api/admin/approval/channel-lead/{leadNo}/audit` | 单级终审；同结论幂等，条件更新防并发 | BOSS/SUPER_ADMIN/SUPER |

### 工单 / 产品
| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/order/list` | 服务单列表（**C7 四维筛选**） | 角色二分：客户仅状态+时间 / 员工四维 |
| `GET /api/mini/order/{orderNo}` | 工单详情 | **客户校验归属 / 员工全量** |
| `GET /api/mini/product/list` | 我的产品 | CHANNEL 看自己的（后端 requireChannel，员工暂不可用） |
| `POST /api/mini/product` | 录入产品（DRAFT） | **仅 CHANNEL**（后端 requireChannel；KB 旧文 CHANNEL/STAFF 不实） |
| `GET /api/mini/product/{code}` | 产品详情（编辑回填） | 仅本人 |
| `PUT /api/mini/product/{code}` | 编辑产品（按 code 定位审批单更新） | 仅本人 |
| `POST /api/mini/product/{code}/submit` | 提交审批 | 仅本人 |
| `POST /api/mini/product/{code}/revoke` | 撤销审批 | 仅本人（PENDING 状态） |
| `POST /api/mini/product/{code}/delete-apply` | 申请删除 | 仅本人（OK 状态） |
| `POST /api/mini/product/{code}/delete-cancel` | 撤销删除 | 仅本人（PENDING_DELETE） |
| `GET /api/mini/partner-product/delete/pending` | **实际路径** 待删列表（历史文档写 /product/pending-delete 有误） | 仅运营/超管 |
| `POST /api/mini/partner-product/delete/{approvalNo}/audit` | **实际路径** 终审删除（历史文档写 /product/{code}/audit-delete 有误） | 仅运营/超管 |
| `GET /api/mini/partner-product/active` | 合作产品只读列表（ACTIVE 且未过期；**无角色守卫，仅校验登录态**） | 已登录 |

### Web 管理端分页扩展（2026-09-02）

| 接口 | 新增/确认契约 |
|------|------|
| `GET /api/admin/attachment/page` | 可组合传 `clientProfileCode`、`orderNo`、`keyword`、`page`、`size`；`keyword` 匹配文件名、资料类型或工单号 |
| `GET /api/admin/strategy-template/page` | 可组合传 `customerGroup`、`keyword`、`status`、`page`、`size`；远程选择器用 `status=ACTIVE` 只取已上线模版 |
| `GET /api/admin/channel-strategy/exists-by-plan/{planCode}` | 按执行计划业务编码精确判断是否被策略引用，不再依赖固定前 200 条分页扫描 |
| `GET /api/admin/order/page` | 列表与详情返回 `ownerStaffName`；服务端按当前页员工编码集合批量补齐，无逐行查询 |
| 产品/下载/分配审批分页 | 返回 `approverStaffName` / `applicantStaffName` / `applicantName` 等姓名字段，编码仅作为接口内部定位字段 |
| 策略与奖励规则分页 | 返回 `bankProductName` / `productName`，按当前页产品编码集合批量补齐 |
| 奖励规则 | `GET/POST /api/admin/reward/rule`；更新由请求体 `ruleVersion` 定位，停用使用 `POST /api/admin/reward/rule/{ruleVersion}/disable`；物理 `id` 不对外返回 |

### 邀请（invitation）
| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/invitation/bind` | 分享链接/二维码携码后自动绑定，保留登录后补绑能力；body `{inviteCode}`，返回 `referrerType`/`referrerName`；仅建立引荐链，不修改服务顾问归属 | 客户本人（`requireClient`） |
| `GET /api/mini/invitation/mine` | 我的邀请码（幂等生成，7 天有效） | 客户本人（`requireClient`） |
| `GET /api/mini/invitation/records?page&size` | 我的邀请记录（经我的邀请码注册的客户），`PageResult<Map>` | 客户本人（`requireClient`） |

### 奖励（reward）
| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/reward/mine/summary` | 我的奖励汇总（`Map`） | 客户本人（`requireClient`） |
| `GET /api/mini/reward/mine?page&size` | 我的奖励记录，`PageResult<Map>` | 客户本人（`requireClient`） |

### 材料上传（G3 · 2026-08-30 新增）
| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/upload` | 上传经营/认证材料（`MultipartFile file` 必填；可选 `bizType` / `clientCode` / **`reportNo`（T2 新增，关联诊断材料回灌）**，由 `@CurrentUser LoanUser user` 取登录态）；落盘 `loan.upload.base-dir`（默认 `./uploads`），best-effort 写 `t_service_attachment` 元数据（失败仅 warn 不阻断）；返回 `LinkedHashMap{fileKey("att"+UUID前32), fileName, fileSize, url:"/api/mini/upload/{fileKey}"}` **+ 新增（向后兼容）`ocrApplied`(bool) / `extractedFields`(数组 `{fieldCode,fieldName,value,confidence}`) / `mergedCount`(int) / `ocrRecordId`(long)** | 已登录（CUSTOMER/STAFF/CHANNEL） |
| `GET /api/mini/upload/{fileKey}` | 按 fileKey 安全回传文件（inline 预览；`fileKey.matches("[a-zA-Z0-9]+")` 防目录穿越，`Files.list` 前缀匹配） | 已登录 |

> 前端：`api/upload.js` 新增 `uploadMaterial(filePath,{bizType,clientCode})` → 走 `uploadImage()` 封装（H5/小程序统一）；`materialUrl(fileKey)` 拼预览地址。已接线：① `pages/match/match.vue` 材料上传 `bizType=m.key`、补充上传 `bizType=OTHER`；② `pages/report/detail.vue` 诊断补充材料 `bizType=FINANCIAL_STATEMENT` → 成功后 `loadDiagnosis(reportNo)` 刷新。业务 ID 遵守红线：fileKey=`att`+32 位随机；`spring.servlet.multipart.max-file-size/request-size=10MB` 已开。

### 微信 H5 JS-SDK

| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/wechat/jssdk/signature?url=...` | 为不含 `#` 片段的当前 H5 页面 URL 生成 `{appId,timestamp,nonceStr,signature}` | 无需登录；网关需保留公开白名单 |

> JS-SDK 使用公众号 `oaAppid/oaSecret`，与微信小程序 AppID/Secret 不是同一套凭证；真实值按上线配置阶段处理。

### 审批中心（统一，T5）

> 方案 A：**无统一审批表**，采用「视图层统一 + 入口统一」（不建表、不迁移数据）。`type=ALL` 为**分段分页**语义：各类型各取一页后内存归并、按 `createdAt` 倒序、截断 `size`，`total` 为三类型 PENDING 之和；前端仅概览、不深翻页。白名单 `loan.mini.approval.types=ALLOCATION`（产品/下载待阶段四）。

| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/approval/counts` | 待审计数 `{PRODUCT, DOWNLOAD, ALLOCATION, TOTAL}` | 运营/超管/老板（OPERATOR/SUPER_ADMIN/SUPER/BOSS） |
| `GET /api/mini/approval/pending?type=ALL\|PRODUCT\|DOWNLOAD\|ALLOCATION&page&size` | 待审列表 `{page,size,total,records(每条约带 type),paginationHint:"SEGMENTED"}` | ALLOCATION 含 DEPT_MANAGER，但部门经理仅可见本人团队；PRODUCT/DOWNLOAD 可含 DEPT_MANAGER |
| `POST /api/mini/approval/{type}/{approvalNo}/audit` | 审批 body `{approve, opinion}` | ALLOCATION 含 DEPT_MANAGER，但部门经理仅可审批本人团队；PRODUCT/DOWNLOAD 可含 DEPT_MANAGER |
| `GET /api/admin/approval/allocation/pending` | 管理端 allocation 待审 | OPERATOR/SUPER_ADMIN/SUPER/BOSS |
| `POST /api/admin/approval/allocation/{approvalNo}/approve` | 管理端 allocation 通过（`ApprovalController`，非 `/audit`） | 同上 |
| `POST /api/admin/approval/allocation/{approvalNo}/reject` | 管理端 allocation 驳回 | 同上 |
| `GET /api/admin/approval/unified/counts` | 管理端统一计数（**2026-08-31 校正**：实际路径带 unified 前缀，旧文写 `/approval/counts` 在代码中不存在） | OPERATOR/SUPER/BOSS |
| `GET /api/admin/approval/unified/pending?type=ALL\|PRODUCT\|DOWNLOAD\|ALLOCATION&page&size` | 管理端统一待审（**2026-08-31 校正**：实际路径带 unified 前缀） | OPERATOR/SUPER/BOSS |
| `POST /api/admin/approval/unified/{type}/{approvalNo}/audit` | 管理端统一终审 body `{approve, opinion}`（**2026-08-31 校正**：`type` 为路径变量，旧文写死 `allocation/{approvalNo}/audit` 不存在） | OPERATOR/SUPER/BOSS |

## 错误码（ResultCode）

```java
COMMON_ERROR("系统繁忙"),
PARAM_ERROR("参数错误"),
UNAUTHORIZED("请先登录"),
FORBIDDEN("无权操作"),
DATA_NOT_FOUND("数据不存在")
```

## 字段命名红线

- **客户姓名**：`contactName`（不是 `name`）
- **企业名称**：`entName`（不是 `enterpriseName`）
- **客户编码**：`clientCode`（不是 `id`）
- **报告编号**：`reportNo`
- **审批单号**：`approvalNo`
- **归属人工号**：`ownerStaffCode`（员工）/ `ownerStaffName`（姓名，C19 接 t_staff）
- **匹配结果态**：`totalResult` ∈ PASS / CONDITION / REJECT / SKIP_SEGMENT_MISMATCH / ERROR

## 管理端业务 ID 契约

- 渠道准入策略以 `strategyCode` 作对外身份：`PUT/DELETE /api/admin/channel-strategy/{strategyCode}`、`POST /{strategyCode}/enable|disable|validate-before-enable`。
- 跨渠道复制传 `sourceStrategyCode`，模版导入传 `templateCode`；创建、复制、导入均返回策略业务编码，不返回自增主键。
- 渠道名单记录以 16 位 `listCode` 作对外身份：`GET/PUT/DELETE /api/admin/channel-user-list/{listCode}`；批量查询/删除传 `{listCodes:[...]}`，分页统一使用组合 Query 模型。
- 产品城市关系以 16 位 `productCityCode` 作对外身份：`GET/PUT/DELETE /api/admin/product-city/relation/{productCityCode}`；批量查询传 `{productCityCodes:[...]}`，组合分页使用 `GET /api/admin/product-city/page`，按产品查询仍使用产品短码 `GET /api/admin/product-city/{productCode}`。
- `listCode` 使用 `culist` 前缀、`productCityCode` 使用 `pcity` 前缀，总长度均为 16；响应实体隐藏物理 `id`。
- 修改接口只用路径业务编码定位；修改体不接收物理 `id`，也不允许改写 `listCode` / `productCityCode`。
- 其余存量管理接口按 `loan-biz-id` 逐域迁移；涉及新增业务编码列/唯一索引时，需先确认数据库迁移方案。

## 前后端契约变更纪律

- 改后端响应字段 → 先查前端调用方 + `落地清单` + `结论沉淀`，再改
- 改前端请求参数 → 同步改后端 DTO + Service + Mapper
- 字段废弃：保留兼容期（默认 null/0），新字段独立

## 跳到

- 业务结论：`06-业务结论沉淀索引（C1-C26）.md`（哪些字段怎么用）
- 数据库：`03-数据模型（DB schema 索引）.md`
