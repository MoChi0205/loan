---
name: loan-gateway-auth
description: >-
  loan-main 网关统一鉴权规范。新增或修改后端 HTTP 接口、配置角色接口授权（t_role_api）、
  控制 Web / 小程序 / 管理端可访问性、排查 401/403 拦截问题时使用；
  所有对外接口必须经 loan-gateway 鉴权（角色 × 接口 × 端），未登记接口一律拦截。
---

# 网关统一鉴权规范（loan-gateway-auth）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "鉴权\|网关\|接口\|403\|401\|权限" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（接口授权属安全边界，不得自行放宽）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增或修改 `loan-service` 的 Controller 接口（会改变接口清单）
- 配置某角色可访问哪些接口（组织权限 → 接口权限）
- 控制某接口是否允许小程序（`MINI_APP`）端访问
- 排查 401 / 403 网关拦截问题
- **每次新增/修改接口后**：重启服务自动同步接口清单，或调 `POST /api/admin/api-perm/sync`

## 核心模型：角色 × 接口 × 端

```
外部请求 → loan-gateway(8088) → ApiAuthGlobalFilter 统一鉴权 → loan-service(8080)
                 │
                 ├─ 白名单（无需登录）：/api/auth/health|public-key|login|channel-login、/api/dict/all、/api/debug/**
                 ├─ 无 token / token 无效 → 401 {code:2000}
                 ├─ 接口未登记 → 403 {code:2001}「接口未登记权限配置」（强制全量登记）
                 ├─ 接口 DISABLED → 403
                 ├─ 端校验：请求头 X-Client-Type(WEB/MINI_APP) 不在接口 client_types → 403「该接口不支持 xx 端访问」
                 ├─ 角色校验（员工）：BOSS 超级角色全量放行；其他查 t_role_api → 无 → 403
                 └─ 类型校验（无角色用户）：CUSTOMER→mini: 前缀、CHANNEL→channel: 前缀（规则 typeRules）→ 不匹配 403
```

## 三张关键表 / 两个 Redis key

| 表 / key | 作用 |
|---|---|
| `t_api_permission` | 接口清单（`api_key` / `http_method` / `path_pattern` / `module_group` / `client_types` / `status`） |
| `t_role_api` | 角色 × 接口授权（`role_code` + `api_key`；BOSS 不落库） |
| Redis `loan:api-perm:rules` | 全量规则 JSON（含 `typeRules` 用户类型维度：CUSTOMER→`mini:` / CHANNEL→`channel:`） |
| Redis `loan:api-perm:version` | 版本号（网关每请求比对，变更即刷新，近实时生效） |

## 接口清单自动同步（不用手工维护种子数据）

- `ApiPermissionSyncService`（`ApplicationRunner`）：启动时扫描 `RequestMappingHandlerMapping` 全部映射，
  自动 upsert 到 `t_api_permission`，`api_key = {模块}:{方法名}`（模块从 `/api/admin/{模块}/…` 提取）
- 首次运行自动给 ADVISER（一线）/ DEPT_MANAGER（管理）插默认授权
- 新增 Controller 接口后**重启服务即自动登记**；也可用 BOSS 调 `POST /api/admin/api-perm/sync` 手动同步

## 新增 / 修改接口的检查清单

- [ ] 新接口路径 `/api/...` 是否符合「模块:方法名」命名（同步后 `api_key` 直观可读）
- [ ] 是否需要限制端：默认 `WEB,MINI_APP` 双端可用；仅 Web 用 `POST /api/admin/api-perm/client-types` 改
- [ ] **管理端是否要加 `ADMIN-WEB`**：报告类接口曾因 `clientTypes` 只有 `WEB,MINI_APP` 被管理端 403
      → axios 吞错 → 表格"暂无数据"（修法见 `loan-web-ui` 自检清单）
      > ⚠️ `clientTypes` 参数是**逗号分隔 String**（不是数组），传数组会 5000 错误
- [ ] 角色授权是否合理：ADVISER 只给一线接口
      （order / lead / client / screening / notification / dashboard / audit / report 部分 / sms 验证码）
- [ ] 管理 / 敏感接口（org 写、blacklist 写、reward 审核、approval 审批、api-perm）仅 BOSS / DEPT_MANAGER
- [ ] 网关匹配优先级：**精确路径优先于通配**（`/order/page` 不会被 `/order/{orderNo}` 抢占，网关已两轮匹配）
- [ ] 服务重启后确认日志 `[ApiPerm] 接口清单同步完成，共 N 个接口`，N 与预期一致

## 网关本地联调（重要）

- 网关配置在 `loan-gateway/src/main/resources/application.yml`
  （**注意：本地启动 `server.port` 需命令行传**，因 nacos-config starter 可能干扰；用
  `-Dspring-boot.run.arguments="--server.port=8088 --spring.redis.host=... --spring.redis.port=... --spring.redis.password=..."`）
- 本地联调路由：`/loan/** → http://127.0.0.1:8080`（生产改 `lb://loan-service`）
- 前端 vite proxy：`/loan → http://localhost:8088`（所有请求经网关）；直连后端调试改 `VITE_API_PROXY=http://localhost:8080`
- JWT 密钥 `jwt.secret` 必须与 `loan-service` 一致（网关只解析不签发）
- 授权 / 端变更实时生效（版本号机制），无需重启网关
- **所有业务接口带 `/loan` context-path**：直连验证形如
  `curl localhost:8080/loan/api/admin/channel-strategy/page?page=1&size=10`

## 常见问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 401 `code:2000` | 无 token / JWT 过期 / 密钥不一致 | 重新登录；核对 `jwt.secret` |
| 403「接口未登记」 | 接口未同步 / path 不匹配 | 重启服务或调 sync；核对 `path_pattern`（**不含 `/loan` 前缀**，网关自动兼容） |
| 403「无权限访问」 | 角色未授权 | 组织权限 → 接口权限 勾选该角色 |
| 403「不支持 xx 端访问」 | `client_types` 不含当前端 | `api-perm/client-types` 调整；前端带 `X-Client-Type` 头 |
| 403「接口已停用」 | `status=DISABLED` | 同步后恢复 `ACTIVE` |
| 502 | 网关转发后端不可达 | 确认 8080 存活；路由 uri 正确 |

> **8080 与 8088 别搞混**：8080 = `loan-service`（改代码后验证直连它，**无需 token**）；
> 8088 = 网关（返回 `{"code":2000,"message":"未登录或会话已过期"}` 是网关鉴权**正常表现**，不是服务挂了）。

## 契约红线速查

- **契约真源**：`db/loan-db-schema.sql`（表数以该文件为准）→ `loan-service` 代码 → `docs/knowledge-base/`
- **审批接口权限真值（D0-4）**：`ALLOCATION` 审批接口仅 `OPERATOR` / `SUPER_ADMIN` / `SUPER` / `BOSS`
  （**不含 DEPT_MANAGER**）；`PRODUCT` / `DOWNLOAD` 才含 `DEPT_MANAGER`。
  依据 `loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java` 的 `APPROVER_ROLES` vs `APPROVAL_ROLES`
- **禁止引用**已失效的 `output/` 与「逻辑蓝图.html」路径（见 `loan-knowledge`）

## 自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 新接口是否被 `ApiPermissionSyncService` 自动登记（日志 N 与预期一致）？
- [ ] `client_types` 是否覆盖所有需要的端（WEB / MINI_APP / ADMIN-WEB）？传参是否用逗号分隔 String？
- [ ] 角色授权是否符合「一线 vs 管理/敏感」划分？
- [ ] 审批类接口是否按 D0-4 真值授权（ALLOCATION 不放 DEPT_MANAGER）？
- [ ] 前端请求头是否带 `X-Client-Type`？
- [ ] 本地联调是否 `curl localhost:8080/loan/...`（确认是 8080 直连验证，非 8088）？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**，D0-4 审批权限真值）
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`
- `docs/knowledge-base/04-后端 API 契约.md#关键接口索引`
- `docs/knowledge-base/04-后端 API 契约.md#错误码（ResultCode）`
- `docs/knowledge-base/04-后端 API 契约.md#前后端契约变更纪律`
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/07-沟通-上线-测试-部署清单.md#本地联调环境（后端不在本地 docker，直连 prd 服务器配置）`
- 管理界面：Web 端「组织权限 → 接口权限」tab（BOSS 配置角色勾选接口）
- 表结构：`db/migrate-api-perm.sql`
- 代码：`loan-gateway/.../filter/ApiAuthGlobalFilter.java`、`loan-service/.../apiperm/**`
- 交叉技能：`loan-backend`、`loan-web-dev`、`loan-service-ops`
