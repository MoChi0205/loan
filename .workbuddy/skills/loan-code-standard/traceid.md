# traceId 全链路追踪规范

> **状态**：✅ 已落地（2026-09-06）
> **实现**：`com.loan.infrastructure.filter.TraceIdFilter`（loan-service）
> **目标**：一次请求在网关、服务端业务日志、访问日志中可用同一个 traceId 串联

---

## 1. 约定

| 项 | 值 |
|----|----|
| 请求头 | `X-Trace-Id` |
| 响应头 | `X-Trace-Id`（**必须回传**，便于前端/网关串联） |
| 日志上下文 key | `traceId`（Log4j2 `ThreadContext`，SLF4J `MDC` 同名可见） |
| 日志输出 | `[traceId=%X{traceId}]` |
| 生成规则 | 无合法入参时 `UUID.randomUUID().toString().replace("-","")` |
| 合法校验 | 长度 ≤ 64 且仅含 `[A-Za-z0-9_-]`；不合法则丢弃并重新生成 |

**过滤器顺序**：`@Order(Ordered.HIGHEST_PRECEDENCE)` —— 必须最先执行，
保证后续所有日志（含异常、AOP、DAO）都能取到 traceId。
`AccessLogFilter` 为 `HIGHEST_PRECEDENCE + 1`，在其之后。

## 2. 透传链路

```
客户端/网关  --X-Trace-Id-->  loan-gateway  --X-Trace-Id-->  loan-service
                                  ^                              |
                                  +---- 响应头 X-Trace-Id -------+
```

- **网关**：生成（无则新建）或透传 `X-Trace-Id`。
- **服务端**：`TraceIdFilter` 复用网关传入的合法值，否则生成新值；写入 `ThreadContext`；响应头回传。
- **清理**：`finally { ThreadContext.remove(CONTEXT_KEY); }` —— 线程池复用必须清理，否则 traceId 串号。

## 3. 在业务代码里写 traceId

**不需要手动传**。`ThreadContext` 是线程绑定的，业务代码正常打日志即可自动带上：

```java
log.info("[邀请绑定] 绑定成功 clientCode={}", clientCode);
// 输出：... [traceId=a3f2c1e...] INFO  c.l.i.service.InvitationService - [邀请绑定] 绑定成功 clientCode=xxx
```

**异步/线程池场景**：`ThreadContext` 不会自动跨线程传递。
新开线程或提交线程池时，需显式拷贝上下文，否则异步日志的 traceId 为空：

```java
String traceId = ThreadContext.get(TraceIdFilter.CONTEXT_KEY);
executor.submit(() -> {
    ThreadContext.put(TraceIdFilter.CONTEXT_KEY, traceId);
    try { /* 业务逻辑 */ } finally { ThreadContext.remove(TraceIdFilter.CONTEXT_KEY); }
});
```

## 4. 踩坑记录（务必阅读）

### 坑 1：`%X{traceId:-none}` 导致 traceId 恒为空

**现象**：配置写 `[traceId=%X{traceId:-none}]`，日志输出 `[traceId=]`（空）。
即使请求头带 traceId、响应头正确回传，日志里依然为空。

**根因**：log4j2 把 `traceId:-none` **整体当作 key 名**去 `ThreadContext` 查找，
找不到该 key → 输出空字符串。它不会回退成"查 traceId、没有则用 none"。

**验证方法**（关键）：先看日志里的线程名。
- `[main]` 线程 = 启动期日志，无请求上下文，traceId 为空属**正常**。
- `[http-nio-8080-exec-N]` = 请求线程，若此处也为空，才是真 Bug。

**修复**：改为 `%X{traceId}`。无值时输出空，有值时输出实际 traceId。

### 坑 2：响应头有值 ≠ 日志有值

排查时分两步验证，不要混为一谈：
```bash
# 步骤1：验证过滤器是否生效（看响应头）
curl -s -D - -o /dev/null -H "X-Trace-Id: TEST123" http://localhost:8080/loan/api/auth/health | grep -i x-trace-id
# 有值 → ThreadContext 里一定有值，问题在日志配置；无值 → 问题在过滤器
```
响应头有值但日志为空 ⇒ 100% 是 PatternLayout 取值语法问题（坑 1）。

## 5. 验收清单

```bash
# 1. 请求日志 traceId 非空
TID="T$(date +%s)"
curl -s -o /dev/null -H "X-Trace-Id: $TID" http://localhost:8080/loan/api/auth/health
grep "$TID" logs/biz.log logs/access.log   # 两个文件都应有命中

# 2. 响应头回传
curl -s -D - -o /dev/null http://localhost:8080/loan/api/auth/health | grep -i x-trace-id

# 3. 不传 traceId 时自动生成
curl -s -D - -o /dev/null http://localhost:8080/loan/api/auth/health | grep -i x-trace-id
# 应返回一个 32 位无横线 UUID

# 4. access 日志 traceId 与 biz 日志可对上
tail -3 logs/access.log
```

## 6. 变更记录

| 日期 | 变更 | 依据 |
|------|------|------|
| 2026-09-06 | 修复 `%X{traceId:-none}` 取值语法；建立本规范 | 用户反馈"traceId 没有在日志中显示"；`docs/plans/Web全页面交互与前后端质量整改-2026-09-03.md` B001 |
