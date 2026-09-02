---
name: loan-service-ops
description: >-
  loan-main 四端服务启停与保活规范。启动/停止/重启/守护后端 loan-service(8080)、
  网关 loan-gateway(8088)、Web loan-web(5173)、小程序 H5 loan-mini(5174) 时使用；解决"服务总是自动停掉"、
  端口误判、JAVA_HOME 失效、进程误伤、旧实例占端口等问题，唯一入口 scripts/service.sh。
---

# 服务启动与保活（loan-service-ops）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "服务\|启动\|端口\|8080\|8088\|5173\|5174\|JAVA_HOME" docs/knowledge-base/10-历史结论与决策日志.md`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（尤其涉及 prd 服务器配置、重启线上服务前必须确认）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

**每次涉及服务启停前必读本规范。**

## 一、唯一入口：scripts/service.sh

```bash
bash scripts/service.sh status                             # 查看四端状态
bash scripts/service.sh start all                          # 启动四端（幂等，已在运行则跳过）
bash scripts/service.sh start backend|gateway|web|mini     # 只启某个
bash scripts/service.sh stop  all|backend|gateway|web|mini # 停止（移除服务并清理端口）
bash scripts/service.sh restart backend               # 重启单个
bash scripts/service.sh watchdog start                # 开启 30s 巡检守护（自动拉起被杀进程）
bash scripts/service.sh watchdog stop                 # 关闭守护
```

- 日志：mini 为 `logs/mini.log`；backend / gateway / Web / watchdog 的运行日志在 `/tmp/loan-*-dev.log`
- 后端与网关启动时先构建可运行 JAR，再复制到 `/tmp` 运行；这是 macOS 后台进程访问 Downloads 限制下的已验证方案
- **禁止**手写 `lsof -ti :8080 | xargs kill` 或裸 `mvn spring-boot:run` 起服务

## 二、为什么服务总是自动停（根因）

1. **会话回收**：工具会话内的后台任务或 `nohup + disown` 在当前 Codex 执行环境中仍会随执行会话回收，
   不能视为可靠保活。
2. **无保活**：`run-dev-prd.sh` 是前台脚本，进程一旦被杀没有任何自愈机制。
3. **误伤**：`lsof -ti :8080` 在 mvn 场景下可能匹配到共享进程树，重启后端时连带杀掉网关。

## 三、保活机制（service.sh 内置）

| 机制 | 说明 |
|---|---|
| `launchctl submit` | 注册当前登录用户级服务；执行调用结束后进程仍存活，不安装系统级守护项 |
| `/tmp` 运行副本 | backend / gateway 的 JAR 与运行日志放 `/tmp`；Web 日志放 `/tmp`，规避后台进程直接访问 Downloads 的权限限制 |
| 幂等启动 | 端口已监听则跳过，重复执行不产生多实例 |
| `watchdog` | 同样由用户级服务托管，每 30s 巡检四端端口，发现停止自动拉起 |
| stop | 先移除服务标签，再清理该服务监听端口，四端互不影响 |

## 四、关键坑（必读）

1. **探测端口用 `curl http://localhost:<port>/`，不要用 `nc 127.0.0.1`** —— vite 只监听 IPv6 `::1`，
   `nc 127.0.0.1` 会误判 DOWN（导致重复启动 / 误报）。
2. **启动服务用普通 Bash 调用 `service.sh start`**；脚本内部交给用户级服务托管，不要再外包一层后台任务。
3. 后端与网关需要先构建，启动时间明显长于 Vite。就绪以 `service.sh status` 和真实 HTTP 请求为准，不写死秒数。
4. 后端启动参数在 `run-dev-prd.sh`（直连 prd Nacos `124.221.150.239:9848`，namespace=prd，不注册服务，关 Dubbo）；
   网关参数在 `service.sh start_gateway`（8088 + Redis `124.221.116.28:9379`）。
5. 改后端代码重启：`service.sh restart backend`；改前端代码 Vite HMR 自动生效，若挂载阶段已异常则 `restart web` 后浏览器强制刷新。

### 本项目当前联调配置源（D41，强制）

- 本地只运行当前代码，数据库、Redis 等后端基础设施配置由 Nacos `prd` 下发。
- 禁止启动或使用本地 Nacos / MySQL / Redis，禁止切换 `dev` namespace，禁止增加本地数据源参数覆盖 Nacos。
- 连接信息与密钥只检查是否成功加载，不复制到代码、脚本、日志或测试文档。
- `scripts/run-dev-local.sh` 仅保留历史开发能力，不得用于当前项目联调与回归。

## 五、验证清单

- [ ] `service.sh status` 四端全部"运行中"
- [ ] `launchctl list | grep com.loan.dev` 能看到 backend / gateway / web / mini 对应标签
- [ ] `curl localhost:5173/`、`curl localhost:5174/`、`curl localhost:8088/loan/api/auth/public-key`、
      `curl localhost:8080/loan/api/auth/public-key` 全 200
- [ ] 新开一个普通短命令后再次执行状态与 curl，确认不是仅在启动会话内存活
- [ ] 需要 watchdog 时，检查 `launchctl` 标签与 `/tmp/loan-watchdog-dev.log`

## 六、易踩坑（2026-08-27 实测）

1. **JAVA_HOME 必须指向 JDK 包内的 Home**：用户指定的 JDK 包根为 `/Users/admin/Documents/developer/jdk1.8`，
   实际 `JAVA_HOME` 是 `/Users/admin/Documents/developer/jdk1.8/Contents/Home`，不能直接拼成包根下的 `bin/java`。报错特征
   `Could not exec java: Cannot run program ".../jre/bin/java": error=2`。
   `service.sh` 固定执行该 JDK 的 `bin/java`；`run-dev-prd.sh` 也将其作为第一候选并实际验证版本；
   `run-dev-prd.sh` 内置 `detect_java8()`（**实际执行 `java -version` 验证**，
   不能只查 `-x` 文件位 —— 坏 JDK 文件在但跑不起来）。
2. **8080 与 8088 别搞混**：8080 = `loan-service`（改代码后验证直连它，**无需 token**）；
   8088 = 网关（返回 `{"code":2000,"message":"未登录或会话已过期"}` 是网关鉴权正常表现，**不是服务挂了**）。
3. **所有业务接口带 `/loan` context-path**：直连验证形如
   `curl localhost:8080/loan/api/admin/channel-strategy/page?page=1&size=10`。
4. **旧实例占端口**：统一执行 `bash scripts/service.sh restart <service>`，不要手工杀共享进程树。
5. **macOS 后台路径限制**：`launchctl` 直接运行 Downloads 内的大型 JAR或向项目日志写入时可能返回 `EX_CONFIG`；
   `service.sh` 已将 backend / gateway 运行副本与相应日志放到 `/tmp`，禁止改回项目目录后直接宣称保活成功。

## 七、契约红线速查

- **后端编译 JDK 8 约束**：禁 `var` / `List.of` / 文本块；编译命令见 `docs/knowledge-base/00-项目结构与代码地图.md#关键命令`
- **网关本地端口需命令行传**（nacos-config starter 干扰），见 `loan-gateway-auth`
- **禁止引用**已失效的 `output/` 与「逻辑蓝图.html」路径（见 `loan-knowledge`）

## 八、自检清单（改完必过）

- [ ] Step 0 结论核对是否已输出？
- [ ] 是否只用 `scripts/service.sh`，未手写 kill / 裸 `mvn spring-boot:run`？
- [ ] 是否用 `curl localhost:<port>/` 探测（未用 `nc 127.0.0.1` 误判 vite）？
- [ ] 是否用普通 Bash 执行 `service.sh start`（未被 `run_in_background` 包裹）？
- [ ] 后端是否使用 JDK 8 + Nacos `prd`，且未配置本地数据库/Redis覆盖？
- [ ] 四端用户级服务标签与端口是否都存在？
- [ ] 四端 curl 验证是否通过？8088 返回 `code:2000` 是否被正确识别为"鉴权正常"而非"服务挂了"？
- [ ] 需要长期保活是否开了 `watchdog start`？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/00-项目结构与代码地图.md#关键命令`（编译 / 构建 / 清理命令）
- `docs/knowledge-base/00-项目结构与代码地图.md#沙盒常见拦截`
- `docs/knowledge-base/07-沟通-上线-测试-部署清单.md#本地联调环境（后端不在本地 docker，直连 prd 服务器配置）`
- `docs/knowledge-base/07-沟通-上线-测试-部署清单.md#部署步骤`
- `docs/knowledge-base/07-沟通-上线-测试-部署清单.md#沙盒踩坑（高频）`
- `docs/knowledge-base/02-业务红线与编码规范.md#沙盒踩坑记录（必读）`
- 脚本：`scripts/service.sh`、`scripts/run-dev-prd.sh`、`scripts/run-dev-local.sh`
- 交叉技能：`loan-gateway-auth`（8088 网关行为与 401/403 判定）
