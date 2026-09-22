# 项目基础设施红线

1. 本机只运行应用进程，不启动或依赖本地 MySQL、Redis、Nacos、OSS/COS 模拟服务或 Docker。
2. 启动 `loan-service`、`loan-gateway` 前必须显式提供 `-Dnacos.server-addr` 与 `-Dnacos.namespace`；缺失时立即失败，禁止回退到 `127.0.0.1`、默认密码或本地文件存储。
3. 数据库、Redis、对象存储、JWT、内部令牌、短信、OCR/AI 等环境配置必须从所选 Nacos 命名空间的 `application.properties`（group=`loan`）读取。
4. 数据库迁移必须先从当前启动所选 Nacos 获取实际数据源，再对该数据源执行；禁止假定 `127.0.0.1:3306/loan_db` 是当前应用数据库。
5. 任何新增基础设施配置不得在代码或启动脚本中写入可工作的本地默认值；测试只能通过 `test`/`l3`/`offline` 专用配置显式隔离。
