# 日志规范（loan-service / loan-gateway）

> **状态**：✅ 已落地（2026-09-06）
> **配置文件**：`loan-service/src/main/resources/log4j2-spring.xml`
> **技术栈**：Log4j2 **2.17.2**（非 logback）+ Spring Boot 2.7.18 + Java 8

---

## 1. 五分类分包约定

按**日志性质**（非业务模块）拆分，每类独立文件、独立滚动策略：

| 分类 | 文件 | 内容 | 产出方式 |
|------|------|------|---------|
| `biz` | `biz.log` | 业务日志（`com.loan` 全量，debug 及以上） | 常规 `LoggerFactory.getLogger(类)` |
| `error` | `error.log` | 错误汇总（error 级别，跨所有分包） | `ThresholdFilter level=error` |
| `access` | `access.log` | 访问日志：耗时 / HTTP 状态 / 业务 code / traceId / IP | `AccessLogFilter` 输出到 logger `ACCESS_LOG` |
| `sql` | `sql.log` | 慢查询（Druid 慢 SQL，阈值 500ms） | logger `com.alibaba.druid` |
| `integration` | `integration.log` | 外部调用：微信 / 短信 / OSS 等第三方边界 | logger `com.loan.wechat` / `.sms` / `.oss` |

**路由原则**：
- `access` 与 `sql` 用 `additivity="false"` —— 只进专属文件，不污染 biz.log。
- `integration` 用 `additivity="true"` —— **既进 integration.log 专属文件，也保留在 biz.log 的完整上下文里**，便于排查业务问题时看到外部调用链路。

## 2. 格式规范

```xml
<!-- 通用 -->
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [traceId=%X{traceId}] %-5level %logger{36} - %msg%n
<!-- access（自包含 traceId，无需 %X） -->
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level - %msg%n
```

access 日志单行样例：
```
2026-09-06 03:58:12.441 INFO - traceId=a3f2... | POST /loan/api/mini/auth/login | status=200 | cost=37ms | bizCode=0 | ip=127.0.0.1 | ua=Mozilla/5.0...
```

字段含义：`方法 URI | HTTP状态 | 耗时 | 业务code | 客户端IP | UA(截断80字符)`
超过 1000ms 的请求以 `WARN SLOW` 前缀输出，便于 grep。

**日志语言**：业务日志用**中文结构化**描述，禁止无意义拼接（如 `"error:" + e`）。
必须带可定位的上下文（业务编码、用户编号、接口名），例：
```java
log.info("[邀请绑定] 绑定成功 clientCode={}, referrer={}", clientCode, referrerName);
```

## 3. 敏感信息脱敏

**实现**：`com.loan.infrastructure.logging.SensitiveRewritePolicy`（Log4j2 `RewritePolicy`），
在日志事件**落盘前**重写消息体。由 `RewriteAppender` 包装目标 appender 生效。

覆盖范围与掩码规则：

| 类型 | 正则要点 | 掩码效果 |
|------|---------|---------|
| JWT / token | 三段式 `eyJ...` | `eyJhbGci.****.****` |
| 密码类键值对 | `password/passwd/pwd/secret/token/access_token/api_key/authorization` + `[:=]`，值用 `.{1,256}` **长度界定** | `password=****` |
| 手机号 | `1[3-9]\d{9}`（负向断言排除更长数字） | `138****8888` |
| 身份证 | 18 位，末位可为 X | `440302********1234` |
| 统一社会信用代码 | 18 位，排除易混字符 | `9144********WXY9` |
| 银行卡号 | 16~19 位 | `************1234` |

**规则顺序有意**：先结构化字段（JWT / 键值对），再裸号码，避免部分改写影响后续匹配。

### 为什么密码值用长度界定，而不是字符集

这是踩坑后确立的**硬性约定**，改动脱敏规则时必须遵守：

字符集方案（无论白名单还是排除式）的本质都是「遇到不在集合内的字符就截断」，一旦截断后
剩余长度不足下限，**整条规则不匹配，密码完全明文落盘**——这比部分掩码更危险，因为毫无迹象。

```
白名单 [A-Za-z0-9._~+/=-]{6,}  匹配 P@ssw0rd123
  → @ 不在集合内，只吃到 "P"（长度 1 < 6）→ 整条规则不匹配 → 密码明文保留 ❌
排除式 [^\s"',;&)}]{4,}        匹配 P@ssw0rd123
  → 能匹配，但遇到引号/逗号即截断，仍存在边界漏掩码风险 ⚠️
长度界定 .{1,256}              匹配 P@ssw0rd123
  → 任意字符都吃，只靠长度收边界 → 整段掩码 ✅
```

**结论**：敏感字段的**值**一律用 `.{N,M}` 长度界定；字符集只用于**结构性识别**
（如手机号 `1[3-9]\d{9}`、身份证 `\d{6}\d{8}\d{3}[\dXx]` 这类格式固定的号码本身）。

**控制台不脱敏**（不落盘，便于本地调试）；所有文件型 appender 均脱敏。

新增 appender 时**必须**套一层 `Rewrite`：
```xml
<Rewrite name="RewriteXxx">
  <SensitiveRewritePolicy/>
  <AppenderRef ref="XxxFile"/>
</Rewrite>
```

## 4. 滚动与保留

| 分类 | 时间 | 大小 | 保留 |
|------|------|------|------|
| biz / error / access | 1 天 (`interval=1 modulate=true`) | 100 MB | 30 份 / 30 天 |
| sql / integration | 1 天 | 50 MB | 15 份 / 15 天 |

归档路径：`${LOG_DIR}/archive/`（error 为 `${LOG_DIR}/error/`），文件名
`%d{yyyy-MM-dd}-loan-platform-service-{分类}-%i.log.gz`，自动 gzip 压缩 + `Delete` 清理过期。

**日志目录**：环境变量 `LOAN_LOG_DIR` 指定绝对路径（后台启动用），未设置时 fallback 到相对 `logs/`（本地前台运行）。

## 5. 硬约束（踩过坑，改动前必读）

1. **`Configuration` 必须声明 `packages="com.loan.infrastructure.logging"`**
   —— Log4j2 靠它扫描 `@Plugin` 注解的脱敏组件。删除或写错包名会导致启动失败：
   `Unable to locate plugin for SensitiveRewritePolicy`。

2. **traceId 只能写 `%X{traceId}`，禁止 `%X{traceId:-none}`**
   —— log4j2 会把 `traceId:-none` **整体当作 key 名**查找，导致请求日志 traceId 恒为空。
   详见 [traceid.md](./traceid.md)。

3. **Java 8**：禁用 `var` / `Map.of` / `List.of` 等 Java 9+ 语法。

4. **`ContentCachingResponseWrapper.copyBodyToResponse()` 会 `content.reset()` 清空缓存**
   —— 必须在复制**之前**提取响应体里的业务 code，否则 `getContentAsByteArray()` 永远返回空数组，
   access 日志的 `bizCode` 恒为 `-`。（2026-09-06 实际踩坑并修复）

5. **响应体解析限量**：仅对 ≤ 8KB 的响应解析业务 code，避免缓存大响应体的内存与拷贝开销。

## 6. 验收清单

```bash
# 1. 五类文件均已生成
ls -la logs/{biz,error,access,sql,integration}.log

# 2. traceId 在请求日志中非空（发请求后 grep 该 traceId）
curl -s -H "X-Trace-Id: VERIFY123" http://localhost:8080/loan/api/auth/health
grep "VERIFY123" logs/biz.log        # 应有命中

# 3. access 日志记录了耗时与业务 code
tail -5 logs/access.log

# 4. 脱敏生效（构造含手机号/密码的日志，确认落盘为掩码）
grep -E "1[3-9][0-9]{9}" logs/biz.log   # 应无明文手机号
```

## 7. 变更记录

| 日期 | 变更 | 依据 |
|------|------|------|
| 2026-09-06 | 建立规范；落地五分类分包 + 脱敏 + traceId 修复 | 《前后端小程序代码与交互优化计划-2026-09-02》阶段2 P1 第 86 / 122 行 |
