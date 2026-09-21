# Loan 系统性能与权限专项审计报告

- 审计日期：2026-09-19
- 审计范围：`loan-service`、`loan-gateway`、`loan-web`、`loan-mini`、数据库建表与迁移脚本
- 审计方式：代码静态审查、权限链路核对、查询与外部调用路径分析
- 证据边界：本报告未连接生产 Nacos、生产数据库和生产 Redis，未执行并发压测；因此“配置是否已由生产环境覆盖”和“具体 QPS/响应时间”需上线前复核。
- 总体结论：当前系统具备网关鉴权、服务内管理端拦截、角色数据范围、分页上限等基础控制，但仍存在可导致身份冒用、服务直连绕过、敏感材料越权读取的高风险问题。性能上，报表多次同步查询、OCR 同步等待、定时任务无界扫描和每请求多次 Redis 往返是主要瓶颈。

## 一、风险总览

| 等级 | 数量 | 处理要求 |
|---|---:|---|
| P0 严重 | 5 | 上线或开放公网前必须完成 |
| P1 高 | 8 | P0 后立即处理，建议 1 个迭代内完成 |
| P2 中 | 3 | 纳入性能与安全加固迭代 |

建议发布门槛：P0 全部关闭；`loan.apiperm.strict=true`；生产启动时校验 Mock、默认密钥和内部令牌均不可用；业务服务只接受网关或可信内网流量。

## 二、权限与安全问题

### AUTH-P0-01：员工登录可仅凭 CRM 用户标识签发令牌

- 位置：`AuthController.java:65-68`；`AuthService.java:170-198`；`ApiAuthGlobalFilter.java:47-56`
- 现象：`POST /api/auth/login` 是公开白名单接口，只需提交 `crmUserId`；服务端按该标识查到在职员工后直接签发 JWT，没有 SSO 票据验签、密码或一次性登录凭证校验。
- 风险：知道或猜到 CRM 用户标识即可冒用员工身份；若冒用老板、运营或超级管理员，影响全部内部客户、材料、报表和审批能力。
- 修复：删除模拟登录；改为校验可信 SSO 的签名票据、`aud/iss/exp/nonce`，再映射员工；上线前为旧接口增加生产环境硬禁用；补充伪造、重放、过期和停用员工测试。
- 验收：仅合法 SSO 回调可签发令牌，直接提交 `crmUserId` 返回 401。

### AUTH-P0-02：渠道账号存在固定明文密码旁路

- 位置：`AuthService.java:311-336`；`ApiAuthGlobalFilter.java:47-56`
- 现象：任意有效渠道手机号配合固定字符串 `loan-sim-pwd` 可跳过 RSA 解密与 BCrypt 校验；渠道登录接口处于公开白名单。
- 风险：渠道账号可被批量接管；渠道数据、产品维护和线索能力可能被滥用。
- 修复：直接删除旁路，不使用仅靠配置关闭的方式保留生产代码；增加连续失败锁定、IP/设备频控和登录告警。
- 验收：固定字符串始终登录失败；正确加密密码才可登录。

### AUTH-P0-03：生产配置未覆盖时，微信 Mock 登录允许任意 code 建号

- 位置：`application.properties:60-64`；`WxCode2SessionService.java:48-54,82-99`；`MiniAuthService.java:96-168`
- 现象：仓库默认 `wechat.mock=true`。Mock 模式按任意调用方提供的 code 生成稳定 openid，并自动创建客户档案和签发 JWT。
- 风险：若生产 Nacos 未明确覆盖，攻击者无需微信身份即可批量注册、登录并调用客户域接口。
- 修复：默认改为 false；在生产 profile 启动时发现 `wechat.mock=true`、占位 appid 或空 secret 立即拒绝启动；Mock 实现仅放入 dev/test profile。
- 验收：生产缺少真实微信配置时服务启动失败，不能降级为 Mock。

### AUTH-P0-04：业务服务默认放行全部请求，可绕过网关

- 位置：`SecurityConfig.java:37-46`；`WebMvcConfig.java:25-32`
- 现象：Spring Security 使用 `.anyRequest().permitAll()`；自定义拦截器只覆盖 `/api/admin/**`、`/api/channel/**`、`/api/debug/**`。`/api/mini/**`、`/api/notification/**`、`/api/sms/**` 和 `/internal/**` 依赖各 Controller 自觉判空或依赖网关。
- 风险：只要 8080 端口可被访问，调用方可绕过网关的角色×接口×端权限。未显式校验登录的接口可匿名调用，其他接口也缺少统一的 fail-closed 认证保证。
- 修复：业务服务网络层仅允许网关访问；Spring Security 将除明确登录/健康白名单外的接口全部设为 authenticated；对内部接口使用 mTLS 或签名头；移除“Controller 自觉鉴权”模式。
- 验收：直连业务服务访问受保护接口返回 401；通过网关且权限正确才成功。

### AUTH-P0-05：小程序材料下载未校验附件归属

- 位置：`MiniUploadController.java:151-177`；`ApiPermissionService.java:272-300`
- 现象：`GET /api/mini/upload/{fileKey}` 只校验 fileKey 字符格式和对象是否存在，不查询附件元数据，也不校验当前用户、客户归属、员工数据范围、材料复核状态或下载审批。
- 风险：获得 fileKey 的任意小程序客户可读取其他客户的身份证、营业执照、流水等敏感材料。随机 fileKey 降低猜测概率，但不能替代对象级授权。
- 修复：下载必须注入当前用户；按 fileKey 查询 `t_service_attachment`；复用客户归属/员工范围校验；敏感材料走审批、动态水印、审计日志和短时一次性下载令牌；禁止原文件直接出库。
- 验收：客户 A 请求客户 B 的 fileKey 返回 403；所有成功下载均有审计记录。

### AUTH-P1-01：接口权限和页面权限存在“缺配置即放行”

- 位置：`ApiPermissionService.java:87-92,158-201`；`AdminAuthInterceptor.java:158-177`
- 现象：`loan.apiperm.strict` 默认 false；接口未登记、角色未授权、页面权限码未配置时均采用保守放行。
- 风险：新增接口或初始化脚本漏执行时，顾问/部门经理可能获得超出职责的管理能力；前端隐藏不能弥补服务端放行。
- 修复：生产固定 `loan.apiperm.strict=true`；未登记接口拒绝；权限码缺失拒绝；CI 对 Controller 路由与权限表做完整性测试。

### AUTH-P1-02：通知接口存在发送和对象级越权

- 位置：`NotificationController.java:73-75,119-121`；`NotificationService.java:172-179`
- 现象：`POST /api/notification/send` 无管理角色限制；单条已读接口只按 `notificationId` 更新，没有把当前 `userNo` 加入条件。
- 风险：在服务直连或错误网关授权条件下，可伪造站内通知；登录用户也可能修改他人通知状态。
- 修复：发送接口迁移到内部服务或限定后台系统角色；标记已读使用 `notificationId + userNo` 联合条件；加入越权测试。

### AUTH-P1-03：默认 JWT 密钥和内部令牌可直接生效

- 位置：`JwtService.java:31-37`；`ApiPermissionController.java:41-43`；`loan-gateway/application.yml:30-38`
- 现象：JWT secret 和内部接口 token 均提供可运行的默认值，内部 token 通过 URL 查询参数传输。
- 风险：配置遗漏时使用公开默认密钥；查询参数可能进入访问日志、代理日志和监控；内部规则接口会暴露完整权限结构。
- 修复：生产缺少随机密钥时拒绝启动；内部 token 放入请求头并定期轮换，优先采用 mTLS；禁止敏感值进入 URL。

### AUTH-P1-04：短信验证码缺少失败次数、IP 和并发原子控制

- 位置：`SmsService.java:53-100,110-123`；`SmsController.java:31-45`
- 现象：仅按手机号限制 60 秒和每日 5 次；验证码校验失败不限次数；“读取计数后写回”不是原子操作；Mock 通道把验证码写入 INFO 日志。
- 风险：可分布式撞库、并发绕过计数；日志读取权限等同于验证码读取权限。
- 修复：按手机号+IP+设备多维限流；失败 5 次销毁验证码并短时锁定；Redis Lua 原子计数；任何环境均不记录验证码明文。

### AUTH-P1-05：Web 和小程序令牌长期存放在本地持久化存储

- 位置：`loan-web/src/store/user.js`；`loan-web/src/utils/storage.js`；`loan-mini/store/user.js`
- 现象：JWT 写入 `localStorage` 或 `uni.setStorageSync`，有效期 24 小时，会话 Redis 为滑动 2 小时。
- 风险：Web 发生 XSS 或终端被调试/备份时令牌可被直接提取；令牌与 Redis 会话有效期口径不一致增加排障复杂度。
- 修复：Web 优先使用 Secure、HttpOnly、SameSite Cookie；若暂不能改造，缩短 access token、使用 refresh token 轮换并强化 CSP；统一响应中的过期时间与真实会话策略。

## 三、性能问题

### PERF-P1-01：材料上传同步等待 VLM，单请求最长约 60 秒

- 位置：`MiniUploadController.java:85-144`；`MiniMaterialService.java:58-92`；`VlmOcrExtractor.java:249-294`；`application.properties:119-123`
- 现象：上传线程依次执行对象存储、附件落库、OCR/VLM HTTP 调用、事实映射和复核单创建；VLM 读取超时为 60 秒，使用每次新建的 `HttpURLConnection`。
- 影响：Tomcat 工作线程被长期占用；并发上传时吞吐迅速下降，并可能触发上游超时重试和重复文件。
- 修复：上传成功后立即返回 `fileKey + recognitionTaskId`；OCR 放入可靠队列/任务表异步执行；设置连接池、并发上限、幂等键、重试与死信；前端轮询任务状态。
- 指标：上传接口 P95 小于 1 秒；OCR 任务独立统计排队、执行和失败耗时。

### PERF-P1-02：报表接口串行发起大量聚合查询

- 位置：`ReportService.java:151-222,231-316,868-947`
- 现象：经营总览会串行查询客户、线索、工单、成交、奖励、初筛、环比、漏斗和多个分布；趋势接口按月份循环查询，12 个月即 12 次 SQL，最多 24 次。受限角色还会先拉出全部工单号/客户号，再生成大 `IN (...)`。
- 影响：数据库往返次数与报表维度、月份和员工数据量线性增长；大部门会产生超长 SQL、内存集合和解析压力。
- 修复：用一次 `GROUP BY DATE_FORMAT(...)` 获取完整趋势；将首页指标合并为条件聚合 SQL；数据范围使用 JOIN/EXISTS，不把全量业务编号搬进 JVM；对 30~60 秒可接受陈旧度的经营看板加缓存。
- 指标：单个报表接口 SQL 次数设预算（建议不超过 6 次），记录 P50/P95 与扫描行数。

### PERF-P1-03：客户/线索回收任务存在无界扫描和逐条操作

- 位置：`ClientAllocationService.java:773-828`；`LeadService.java:457-522`
- 现象：任务一次加载全部到期记录。客户回收逐条更新；客户和线索预警逐条执行“是否通知”查询再插入通知。
- 影响：数据增长后形成长事务、高内存占用、锁持有时间过长和典型 N+1；任务失败会导致整批回滚或重复执行。
- 修复：按主键游标每批 200~500 条；批量 UPDATE/INSERT；以唯一键实现幂等并直接捕获冲突，移除逐条 exists；每批独立事务并记录断点。

### PERF-P2-01：认证链路每个请求至少产生多次 Redis 往返

- 位置：`ApiAuthGlobalFilter.java:101-135`；`ApiRuleService.java:72-88`；`JwtAuthenticationFilter.java:77-89`；`AuthService.java:251-283`
- 现象：网关每次请求读取规则版本；业务服务再读取 Redis 会话，并对会话执行一次 `EXPIRE` 滑动续期。
- 影响：单业务请求在进入业务逻辑前通常已有 3 次 Redis 操作；高 QPS 下 Redis 延迟直接叠加到所有接口。
- 修复：规则版本采用本地短 TTL 或发布订阅推送；会话读取后仅在剩余 TTL 低于阈值时续期，或以 Lua 合并读取与条件续期；监控 Redis P95 和命令量。

### PERF-P2-02：本地材料查询按目录扫描，下载最多重复探测 9 次

- 位置：`OcrService.java:191-210`；`MiniUploadController.java:163-169`
- 现象：OCR 按 fileKey 使用 `Files.list` 扫描整个上传目录；下载为兼容扩展名逐个调用 `objectExists`，找到后还会再次检查。
- 影响：文件数增长后识别变为 O(N) 目录扫描；云对象存储模式下每次预览可能产生多次远程 HEAD 请求。
- 修复：附件表保存完整 objectKey；所有读取直接按 objectKey 定位；下载前只查询一次附件元数据和一次对象存储。

### PERF-P2-03：客户回收扫描缺少匹配查询条件的复合索引

- 位置：`ClientAllocationService.java:779-809`；`db/loan-db-schema.sql:519-526`
- 现象：查询条件为 `owner_staff_code IS NOT NULL AND (last_followed_at IS NULL OR last_followed_at < ?)`；现有客户表只有 `owner_staff_code` 单列索引，没有覆盖 `last_followed_at` 的复合索引。
- 影响：随着客户量增长，定时任务可能扫描大量已归属客户并回表。
- 修复：结合实际 `EXPLAIN ANALYZE` 评估增加 `(owner_staff_code, last_followed_at, id)` 或将 `NULL` 分支拆成可索引查询；批处理改造后再确定最终索引顺序。

## 四、已有有效控制

| 控制 | 证据 | 评价 |
|---|---|---|
| 网关规则不可用时拒绝请求 | `ApiAuthGlobalFilter.java:126-135` | 正确的 fail-closed 行为，但业务服务直连仍需封闭 |
| 渠道与客户的数据范围已有专用服务/守卫 | `AdminAuthInterceptor.java:110-139` 及各 mini Controller | 基础方向正确，仍需对象级授权统一化 |
| 分页插件最大 100 条 | `MybatisPlusConfig.java:23-28` | 可控制普通分页接口单次返回量 |
| 报表角色范围由服务端计算 | `ReportService.java:98-135` | 避免仅依赖前端菜单隐藏 |
| 慢 SQL 与慢接口日志已配置 | `application.properties:33-49` | 可用于修复后的量化验收 |

## 五、整改路线

### P0：发布阻断项（预计 2~4 个开发日）

1. 删除员工模拟登录、渠道固定密码旁路，生产强制关闭微信 Mock。
2. 封闭业务服务端口，并把 Spring Security 改为默认认证、最小白名单。
3. 为材料预览/下载增加附件归属、角色数据范围、审批、水印和审计校验。
4. 将权限模式改为严格拒绝，校验所有 Controller 路由均完成登记和授权。
5. 替换默认 JWT/internal token，修复通知对象级授权和验证码日志。

### P1：性能主链路（预计 3~5 个开发日）

1. OCR 改为上传与识别解耦的异步任务。
2. 报表趋势改成单次分组聚合，总览合并指标 SQL。
3. 回收与预警任务改为游标分批、批量写、唯一键幂等。
4. 网关规则版本和服务会话续期降低 Redis 往返。
5. 以附件 objectKey 替代目录扫描和多次对象探测。

## 六、验收清单

| 验收项 | 通过标准 |
|---|---|
| 身份认证 | 模拟 CRM、`loan-sim-pwd`、任意微信 code 在生产均不可获得令牌 |
| 服务直连 | 业务服务直连受保护接口一律 401/403；仅网关可信链路可用 |
| 对象权限 | 客户、渠道、顾问对非本人客户的报告、材料、通知操作均返回 403 |
| 权限完整性 | 新增未登记接口在测试与运行期都默认拒绝，不存在缺配置放行 |
| 上传性能 | 文件保存 P95 < 1 秒，AI 识别异步且可查询状态、可重试、幂等 |
| 报表性能 | 12/24 月趋势均单次聚合；核心报表 SQL 次数、P95、扫描行数达到预算 |
| 批处理 | 单批有上限、可断点续跑、失败不回滚全部历史进度 |
| 密钥配置 | 生产占位密钥、空微信密钥、Mock 开关会触发启动失败 |

## 七、审计结论

当前最需要优先处理的不是页面权限显示，而是身份入口和服务端的默认信任边界。网关已有较完整的 fail-closed 设计，但业务服务 `.permitAll()`、模拟身份入口和对象级下载授权缺失，会使网关控制在部署或配置失误时失效。性能问题目前以结构性风险为主；在未压测前不能给出容量结论，但同步 60 秒 AI 调用、按月循环 SQL 和无界批处理已足以作为改造依据。
