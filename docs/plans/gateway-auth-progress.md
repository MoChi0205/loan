# 网关统一鉴权落地记录（角色 × 接口 × 端）

> 2026-08-26 ｜ 需求：所有接口经网关鉴权；不同角色可访问接口不同；Web 与小程序端可访问接口不同

## 交付内容

### 1. 数据模型（`db/migrate-api-perm.sql`，已执行）
- `t_api_permission`：接口权限定义（api_key 唯一 / http_method / path_pattern / module_group / client_types(WEB,MINI_APP) / status）
- `t_role_api`：角色 × 接口授权（uk_role_api；BOSS 超级角色不落库）
- Redis：`loan:api-perm:rules`（全量规则 JSON）+ `loan:api-perm:version`（版本号，网关近实时感知）

### 2. loan-service（`com.loan.apiperm`）
- `ApiPermissionSyncService`：启动自动扫描 `RequestMappingHandlerMapping` 登记全部接口（当前 **90 个**），首次自动默认授权（ADVISER 49 / DEPT_MANAGER 74）
- `ApiPermissionService`：规则构建 → Redis 下发；管理逻辑（分页/角色回显/保存/改端/同步）
- `ApiPermissionController`：`/api/admin/api-perm/*`（仅 BOSS）+ 内部 `/internal/api-perm/rules?token=`（网关兜底）

### 3. loan-gateway（`com.loan.gateway`，从空壳到可用）
- `GatewayApplication` + `application.yml`（本地联调路由 `/loan/** → http://127.0.0.1:8080`）
- `GatewayJwtUtil`：与服务端同密钥解析 JWT（只解析不签发）
- `ApiRuleService`：Redis 版本号比对 + 全量缓存 + 内部接口兜底
- `ApiAuthGlobalFilter`：白名单 → JWT 认证 → 端识别（X-Client-Type）→ 接口匹配（**精确优先于通配**）→ 端校验 → 角色校验（BOSS 全量）→ 透传身份头

### 4. 前端
- `utils/request.js`：全局注入 `X-Client-Type: WEB` 头
- `views/org/OrgCenter.vue`：新增「接口权限」tab（角色列表 + 接口分组树勾选 + 保存，BOSS 只读提示）
- `vite.config.js`：proxy `/loan → http://localhost:8088`（Web 全链路经网关）

## 端到端验证（全部通过）

| 场景 | 结果 |
|------|------|
| 无 token 访问受保护接口 | 401 {code:2000} |
| 白名单 login / public-key | 200 |
| BOSS 访问业务+管理接口（order/org/reward/api-perm 等） | 全 200 |
| ADVISER 访问一线接口（order:page / lead:page） | 200 |
| ADVISER 越权访问管理接口（org/reward/blacklist/api-perm） | 403 {code:2001}「无权限访问该接口」 |
| 未登记接口 | 403「接口未登记权限配置」 |
| order:page 改仅 WEB 后 MINI_APP 访问 | 403「该接口不支持 MINI_APP 端访问」 |
| 恢复双端后 MINI_APP | 200（**实时生效**，无需重启网关） |
| 前端 5173 → 网关 8088 → 后端全链路 | 200 |

## 踩坑记录

1. **无参 `@PostMapping` 文本解析**：`([^)]*)` 会吞掉后续 `@OpLog(...)` → 改用运行时 `RequestMappingHandlerMapping` 扫描（100% 准确，新增接口自动登记）
2. **`getPatternsCondition()` 在 Spring 5.3 可能返回 null** → 增加 `getPathPatternsCondition()` 兜底
3. **网关 application.yml 的 server.port 未生效**（nacos-config starter 干扰，随机端口 65253）→ 本地启动用命令行 `--server.port=8088`（正式环境从 Nacos 配置）
4. **路径通配抢占**：`/api/admin/order/page` 被 `/api/admin/order/{orderNo}` 先匹配 → 两轮匹配（精确优先）
5. **context-path 差异**：请求 path 含 `/loan` 前缀，登记的 pattern 不含 → 网关匹配时兼容去掉首段
6. **网关进程被 SIGKILL(137)**：重启解决（内存压力），验证过程中注意端口存活

## 后续建议

- 小程序端请求加 `X-Client-Type: MINI_APP`（小程序基础库 request 拦截注入）
- 生产网关路由改 `lb://loan-service`（Nacos 服务发现）
- 敏感写操作可加 `@RequireRole` 注解双保险（服务端二次校验）
