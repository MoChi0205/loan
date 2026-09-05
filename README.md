# loan-platform · 企业贷款咨询服务产品系统

项目已形成后端服务、统一网关、Web 管理端与 uni-app 客户端（H5 / 微信小程序）四端联动。核心链路覆盖登录鉴权、客户资料、规则匹配、报告、工单、审批、渠道与管理后台。

## 技术栈

| 端 | 技术栈 | 本地端口 |
|----|--------|----------|
| 后端服务 | Java 8 · Spring Boot 2.7.18 · MyBatis-Plus · Redis · Nacos | 8080 |
| 统一网关 | Spring Cloud Gateway | 8088 |
| Web 管理端 | Vue 3 · Vite 5 · Element Plus · Pinia · Axios | 5173 |
| uni-app 客户端 | Vue 3 · uni-app（H5 / mp-weixin） | H5 5174 |
| 数据库 | MySQL 8（`loan_db`） | 以 `db/loan-db-schema.sql` 为准 |

## 目录结构

```text
loan-main/
├── loan-api/              # 跨模块 DTO / API 契约
├── loan-service/          # 后端业务服务与管理接口
├── loan-gateway/          # 统一鉴权、路由与访问控制
├── loan-web/              # Web 管理端
├── loan-mini/             # uni-app H5 / 微信小程序
├── db/                    # schema、初始化与迁移脚本
├── nacos/                 # dev / prd 配置与导入脚本
├── scripts/               # 四端启停、构建与联调脚本
└── docs/knowledge-base/   # 当前知识库与历史决策
```

数据库对象不要在文档中写死数量；需要当前值时执行：

```bash
rg -c '^CREATE TABLE' db/loan-db-schema.sql
```

## 本地四端联调

当前联调模式由本地后端连接既有 Nacos / MySQL / Redis，所有 Web 与客户端业务请求统一经过 8088 网关。

```bash
bash scripts/service.sh status
bash scripts/service.sh start all
bash scripts/service.sh restart backend
bash scripts/service.sh stop mini
```

访问地址：

- Web 管理端：`http://localhost:5173`
- uni-app H5：`http://localhost:5174`
- 网关：`http://localhost:8088/loan`
- 后端直连仅用于诊断：`http://localhost:8080/loan`

`loan-web` 与 `loan-mini` 的开发代理默认均指向网关；不要把前端默认代理改回 8080。

## 构建验证

```bash
mvn -q -pl loan-service -am test
cd loan-web && npm test && npm run build
cd ../loan-mini
npm test
npm run build:h5
npm run build:mp-weixin
```

Maven 默认执行单元测试；只有联调启动和明确的快速打包场景由脚本显式传入 `-DskipTests`。

## 接口约定

- 统一响应：`{ code, message, data, traceUuid }`，`code=0` 表示成功。
- 分页：`PageResult{page,size,total,records}`。
- 管理端、客户端均由网关执行统一鉴权；后端仍保留自身权限校验。
- 角色与审批边界以 `docs/knowledge-base/01-角色权限模型.md` 和历史决策台账为准。

## 当前成熟度

四端核心链路已可联调，Maven 默认测试与 Web / mini 调用层门禁已可执行；但个人客群、部分占位域、Web / mini 页面与业务流测试深度、存量业务 ID 迁移、渠道绑定与审批后续动作仍需逐项完善。真实微信 AppID、HTTPS 合法域名及生产部署配置按计划放在最后处理。

开发前先阅读 `docs/knowledge-base/README.md` 与 `docs/knowledge-base/10-历史结论与决策日志.md`，再按涉及业务域读取对应知识库与 `.workbuddy/skills/`。
