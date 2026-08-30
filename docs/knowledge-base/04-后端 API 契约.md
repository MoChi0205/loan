# 04 · 后端 API 契约（小程序端 /api/mini/*）

> **最新校正**：2026-08-30 据 `loan-service` 全部 `com.loan.mini.controller.*` 源码逐接口复核，修正以下陈旧项——① `match/report/{reportNo}/products|diagnosis` → 实际归属 `MiniMatchController` 的 `report/{reportNo}/products|diagnosis`；② 产品操作路径 `{no}` → `{code}`；③ 分配审批 `{no}` → `{approvalNo}`；④ 工单/报告 `{no}` → `{orderNo}/{reportNo}`；⑤ `auth/login-bind` 已废弃（合并进 `auth/login.inviteCode`）；补 `auth/enterprise`、`wecom/qrcode`、`client`(录入)。以本文 + 源码为准。

## 通用约定

- Base URL：`loan-service/api/mini/*`（小程序端）
- 管理端：`/api/admin/**` —— **统一鉴权**（AdminAuthInterceptor）：需登录 + STAFF + 管理角色（BOSS/DEPT_MANAGER/OPERATOR/SUPER_ADMIN/SUPER），2026-08-30 阶段三落地，匿名/客户/渠道一律 403
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
| `GET /api/mini/me` | 当前用户档案摘要（含 `roleInfo`） | 已登录 |
| ~~`POST /api/mini/auth/login-bind`~~ ⚠️ 已废弃 | 合并进 `POST /api/mini/auth/login` 的 `inviteCode` 参数（2026-08-30 校正，旧方案文档仍写 login-bind 不实） | — |
| `POST /api/mini/auth/enterprise` | 企业/个人认证（CUSTOMER） | 已登录(CUSTOMER) |
| `GET /api/mini/wecom/qrcode` | 企微客服活码 URL | 公开 |

### 匹配（C15）
| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/match/run` | 发起匹配；body 可传 `clientCode`（**C2 替客匹配必传目标客户**；客户不传用登录态） | CUSTOMER / STAFF；CHANNEL **禁入（后端已守卫）** |
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
| `POST /api/mini/client` | 录入新客户（自动归属） | 仅 STAFF |
| `POST /api/mini/client/{code}/claim` | 申请分配（AUTO_CLAIMED 或 PENDING_APPROVAL） | 仅 STAFF |
| `GET /api/mini/client/{code}/claim-status` | 分配审批状态轮询 | 仅 STAFF |
| ~~`GET /api/mini/client/allocation-approvals/pending`~~ ⚠️ 已废弃（兼容期） | 待审列表 → 改用 `GET /api/mini/approval/pending?type=ALLOCATION` | STAFF + OPERATOR/SUPER/BOSS |
| ~~`POST /api/mini/client/allocation-approvals/{approvalNo}/approve`~~ ⚠️ 已废弃（兼容期） | 通过 → 改用 `POST /api/mini/approval/ALLOCATION/{approvalNo}/audit` | 同上 |
| ~~`POST /api/mini/client/allocation-approvals/{approvalNo}/reject`~~ ⚠️ 已废弃（兼容期） | 驳回 → 改用 `POST /api/mini/approval/ALLOCATION/{approvalNo}/audit` | 同上 |

### 线索录入（融资需求，T4 · 渠道走 Lead）

| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/lead/submit` | 录入融资需求线索；body 新增企业字段 `entName`/`creditCode`/`industry`/`foundYears`/`annualTaxAmount`/`annualInvoiceAmount`（**全部可选、前端字符串**）；`source` 由后端按用户类型派生：**CHANNEL→进公海 / BOSS→BOSS / 其他 STAFF→ADVISER / CUSTOMER→VIP**（旧 `MINI` 字典已废弃）；响应由 `Result<String>` 改 `Result<Map>`：`{leadNo, duplicated}`，重复时 `msg="该客户已被录入，请联系运营"` 且**不泄归属人** | **含 CHANNEL**（沙箱隔离） |
| `GET /api/mini/lead/my?page&size` | **我录入的线索**；`PageResult<Map>`，字段 `leadNo`/`contactName`(脱敏)/`entName`/`phone`(掩码)/`followStatus`/`createdAt`，**仅本人录入** | 已登录 |

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

### 材料上传（G3 · 2026-08-30 新增）
| 接口 | 说明 | 权限 |
|------|------|------|
| `POST /api/mini/upload` | 上传经营/认证材料（`MultipartFile file` 必填；可选 `bizType` / `clientCode` / **`reportNo`（T2 新增，关联诊断材料回灌）**，由 `@CurrentUser LoanUser user` 取登录态）；落盘 `loan.upload.base-dir`（默认 `./uploads`），best-effort 写 `t_service_attachment` 元数据（失败仅 warn 不阻断）；返回 `LinkedHashMap{fileKey("att"+UUID前32), fileName, fileSize, url:"/api/mini/upload/{fileKey}"}` **+ 新增（向后兼容）`ocrApplied`(bool) / `extractedFields`(数组 `{fieldCode,fieldName,value,confidence}`) / `mergedCount`(int) / `ocrRecordId`(long)** | 已登录（CUSTOMER/STAFF/CHANNEL） |
| `GET /api/mini/upload/{fileKey}` | 按 fileKey 安全回传文件（inline 预览；`fileKey.matches("[a-zA-Z0-9]+")` 防目录穿越，`Files.list` 前缀匹配） | 已登录 |

> 前端：`api/upload.js` 新增 `uploadMaterial(filePath,{bizType,clientCode})` → 走 `uploadImage()` 封装（H5/小程序统一）；`materialUrl(fileKey)` 拼预览地址。已接线：① `pages/match/match.vue` 材料上传 `bizType=m.key`、补充上传 `bizType=OTHER`；② `pages/report/detail.vue` 诊断补充材料 `bizType=FINANCIAL_STATEMENT` → 成功后 `loadDiagnosis(reportNo)` 刷新。业务 ID 遵守红线：fileKey=`att`+32 位随机；`spring.servlet.multipart.max-file-size/request-size=10MB` 已开。

### 审批中心（统一，T5）

> 方案 A：**无统一审批表**，采用「视图层统一 + 入口统一」（不建表、不迁移数据）。`type=ALL` 为**分段分页**语义：各类型各取一页后内存归并、按 `createdAt` 倒序、截断 `size`，`total` 为三类型 PENDING 之和；前端仅概览、不深翻页。白名单 `loan.mini.approval.types=ALLOCATION`（产品/下载待阶段四）。

| 接口 | 说明 | 权限 |
|------|------|------|
| `GET /api/mini/approval/counts` | 待审计数 `{PRODUCT, DOWNLOAD, ALLOCATION, TOTAL}` | 运营/超管/老板（OPERATOR/SUPER_ADMIN/SUPER/BOSS） |
| `GET /api/mini/approval/pending?type=ALL\|PRODUCT\|DOWNLOAD\|ALLOCATION&page&size` | 待审列表 `{page,size,total,records(每条约带 type),paginationHint:"SEGMENTED"}` | 同上（ALLOCATION 含 DEPT_MANAGER） |
| `POST /api/mini/approval/{type}/{approvalNo}/audit` | 审批 body `{approve, opinion}` | 同上（ALLOCATION 含 DEPT_MANAGER） |
| `GET /api/admin/approval/allocation/pending` | 管理端 allocation 待审 | OPERATOR/SUPER/BOSS/DEPT_MANAGER |
| `POST /api/admin/approval/allocation/{approvalNo}/audit` | 管理端 allocation 终审 | 同上 |
| `GET /api/admin/approval/counts` | 管理端统一计数 | OPERATOR/SUPER/BOSS |
| `GET /api/admin/approval/pending?type=ALL\|PRODUCT\|DOWNLOAD\|ALLOCATION&page&size` | 管理端统一待审 | OPERATOR/SUPER/BOSS |

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

## 前后端契约变更纪律

- 改后端响应字段 → 先查前端调用方 + `落地清单` + `结论沉淀`，再改
- 改前端请求参数 → 同步改后端 DTO + Service + Mapper
- 字段废弃：保留兼容期（默认 null/0），新字段独立

## 跳到

- 业务结论：`06-业务结论沉淀索引（C1-C19）.md`（哪些字段怎么用）
- 数据库：`03-数据模型（DB schema 索引）.md`