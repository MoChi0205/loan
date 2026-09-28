# loan-platform

企业贷款咨询服务系统，包含后端服务、统一网关、Web 管理端和 uni-app 客户端（H5 / 微信小程序）。当前文档只描述现行代码与业务规则。

## 模块与端口

| 模块 | 技术栈 | 端口 |
|---|---|---:|
| `loan-service` | Java 8 / Spring Boot 2.7 / MyBatis-Plus | 9080 |
| `loan-gateway` | Spring Cloud Gateway | 9088 |
| `loan-web` | Vue 3 / Vite / Element Plus | 9173 |
| `loan-mini` H5 | Vue 3 / uni-app | 9174 |
| `loan-mini` 微信小程序 | uni-app / mp-weixin | 微信开发者工具 |

Web、H5 和小程序业务请求统一经过 9088 网关；9080 直连只用于后端诊断。

## 基础设施红线

1. 本机只运行应用，不启动本地 MySQL、Redis、Nacos、OSS/COS 模拟服务或 Docker。
2. 后端和网关启动时必须显式提供 `-Dnacos.server-addr` 与 `-Dnacos.namespace`，缺失即失败。
3. 数据库、Redis、对象存储、JWT、内部令牌、短信和 OCR/AI 配置从所选 Nacos 命名空间的 `application.properties`（group=`loan`）读取。
4. 数据库迁移必须先读取本次启动所选 Nacos 的真实数据源，禁止假定本地数据库地址。

开发环境示例：

```text
-Dnacos.server-addr=124.221.150.239:9848
-Dnacos.namespace=prd
```

## 本地联调

```bash
bash scripts/service.sh status
bash scripts/service.sh start all
bash scripts/service.sh restart backend
bash scripts/service.sh stop mini
```

- Web：`http://localhost:9173`
- H5：`http://localhost:9174`
- 网关：`http://localhost:9088/loan`
- 后端诊断：`http://localhost:9080/loan`

## 验证

```bash
mvn -q -pl loan-service -am test
cd loan-web && npm test && npm run build
cd ../loan-mini && npm test && npm run build:h5 && npm run build:mp-weixin
```

开始开发前阅读 [`docs/knowledge-base/README.md`](docs/knowledge-base/README.md)，并按业务域读取对应现行文档。
