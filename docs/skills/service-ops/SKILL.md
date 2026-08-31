# 服务启动与保活（Service Ops）

**用途**：启动/停止/重启/守护 loan 三端服务（后端 8080 / 网关 8088 / 前端 5173），解决"服务总是自动停掉"。

**每次涉及服务启停前必读本规范。**

## 一、唯一入口：scripts/service.sh

```bash
bash scripts/service.sh status                        # 查看三端状态
bash scripts/service.sh start all                     # 启动三端（幂等，已在运行则跳过）
bash scripts/service.sh start backend|gateway|web     # 只启某个
bash scripts/service.sh stop  all|backend|gateway|web # 停止（按端口精确 kill，互不误伤）
bash scripts/service.sh restart backend               # 重启单个
bash scripts/service.sh watchdog start                # 开启 30s 巡检守护（自动拉起被杀进程）
bash scripts/service.sh watchdog stop                 # 关闭守护
```

- 日志统一在 `loan-main/logs/{backend,gateway,web,watchdog}.log`，PID 在 `/tmp/loan-*.pid`
- **禁止**手写 `lsof -ti :8080 | xargs kill` 或裸 `mvn spring-boot:run` 起服务

## 二、为什么服务总是自动停（根因）

1. **会话回收**：用工具的后台任务（run_in_background）或前台 `&` 启动的服务，是当前会话的**子进程**，会话结束/回合回收时整个进程组被杀 → 这是历史反复"自动停掉"的主因。
2. **无保活**：`run-dev-prd.sh` 是前台脚本，进程一旦被杀没有任何自愈机制。
3. **误伤**：`lsof -ti :8080` 在 mvn 场景下可能匹配到共享进程树，重启后端时连带杀掉网关。

## 三、保活机制（service.sh 内置）

| 机制 | 说明 |
|---|---|
| `nohup` + `&` + `disown` | 忽略 SIGHUP、移出作业表；**macOS 没有 setsid**，用此组合让进程脱离会话（后端进程 reparent 到 PID 1） |
| 幂等启动 | 端口已监听则跳过，重复执行不产生多实例 |
| `watchdog` | 独立守护进程，每 30s 巡检三端端口，发现停止自动 `service.sh start <svc>` 拉起 |
| 按端口 stop | 每个服务只 kill 自己端口进程，网关/后端互不影响 |

## 四、关键坑（必读）

1. **探测端口用 curl `http://localhost:<port>/`，不要用 `nc 127.0.0.1`** —— vite 只监听 IPv6 `::1`，`nc 127.0.0.1` 会误判 DOWN（导致重复启动/误报）。
2. **启动服务用普通 Bash 调用执行 `service.sh start`**（脚本内部自己 `&` 后台化），**不要用 run_in_background 包裹**（会被会话回收）。
3. 后端首次启动慢（mvn install + 编译，约 1-2 分钟）；网关约 30s；vite 约 300ms。就绪检查：`nc -z` 8080/8088 + `curl localhost:5173`。
4. 后端启动参数在 `run-dev-prd.sh`（直连 prd Nacos `124.221.150.239:9848`，namespace=prd，不注册服务，关 Dubbo）；网关参数在 `service.sh start_gateway`（8088 + Redis `124.221.116.28:9379`）。
5. 改后端代码重启：`service.sh restart backend`；改前端代码 vite HMR 自动生效，必要时 `restart web`。

## 五、验证清单

- [ ] `service.sh status` 三端全部"运行中"
- [ ] `curl localhost:5173/`、`curl localhost:8088/loan/api/auth/public-key`、`curl localhost:8080/loan/api/auth/public-key` 全 200
- [ ] 进程已脱离会话：`ps -o ppid,command -p <pid>` 后端 bash 的 PPID 为 1
- [ ] `watchdog start` 后手动 `kill` 一个进程，30s 内自动拉起（查 logs/watchdog.log）

## 六、易踩坑（2026-08-27 实测）

1. **JAVA_HOME 失效 = mvn 启动必挂**：`~/.bash_profile` 曾写死 `/Users/admin/Documents/developer/jdk1.8`（该目录 java 无执行权限），报错特征
   `Could not exec java: Cannot run program ".../jre/bin/java": error=2`。
   **已修复**：`~/.bash_profile` 改为优先探测 `/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home`；
   `run-dev-prd.sh` 内置 `detect_java8()`（**实际执行 `java -version` 验证**，不能只查 `-x` 文件位——坏 JDK 文件在但跑不起来）。
2. **8080 与 8088 别搞混**：8080 = loan-service（改代码后验证直连它，**无需 token**）；8088 = 网关（返回 `{"code":2000,"message":"未登录或会话已过期"}` 是网关鉴权正常表现，不是服务挂了）。
3. **所有业务接口带 `/loan` context-path**：直连验证形如 `curl localhost:8080/loan/api/admin/channel-strategy/page?page=1&size=10`。
4. **旧实例占端口**：`mvn spring-boot:run` 绑定失败报 `Port 8080 was already in use` 时，先 `lsof -ti:8080 -sTCP:LISTEN` 找到 java PID 再 kill，然后重启。

