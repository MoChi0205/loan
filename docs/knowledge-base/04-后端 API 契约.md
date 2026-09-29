# 后端 API 契约

## 通用约定

- 前端统一访问 `http://localhost:9088/loan`；网关转发到 9080 服务。
- 统一响应：`{ code, message, data, traceUuid }`，`code=0` 表示成功。
- 分页响应：`{ page, size, total, records }`。
- 登录身份由服务端 token 解析；禁止接受前端传入角色或员工编号作为授权依据。
- 查询可请求更窄的 `SELF / DEPARTMENT / COMPANY`，后端必须校验不得超过角色上限。

## 关键接口域

| 域 | 路径前缀 | 说明 |
|---|---|---|
| Web 登录 | `/api/auth` | 图片验证码、验证码登录、密码登录、找回密码、当前用户 |
| 小程序登录 | `/api/mini/auth` | 微信登录、手机号一键登录、客户身份 |
| 客户 | `/api/admin/client` | 列表、详情、历史、归属和跟进；对象级鉴权 |
| 服务运营 | `/api/admin/appointment`、`/api/admin/outing`、`/api/admin/workbench` | 预约、外出、每日名单、日期范围和未完成事项 |
| 审批 | `/api/admin/approval` | 我的申请、待审批、分配、下载等统一审批 |
| 手机号查看 | `/api/admin/lead/sensitive` | 解锁、额度、超限审批和留痕 |
| 报告 | `/api/admin/report`、`/api/mini/report` | 员工聚合报告与客户经营报告隔离；初筛同时生成双视角快照与 PDF 归档 |
| 通知 | `/api/notification`、`/api/mini/notification` | 消息列表、未读数和已读状态 |

## 输出约束

- 客户端 DTO 不得出现银行产品、内部准入、渠道匹配或内部规则命中信息。
- 页面 DTO 返回名称和中文释义；内部编码仅用于请求定位时传输，不作为页面文案展示。
- 手机号列表字段只返回脱敏值；明文只由敏感查看接口按权限单次返回。
- 错误响应不得包含 SQL、密钥、手机号明文、token 或内部堆栈。

## 服务台查询参数

- `/day` 和 `/daily-lists` 支持 `dateFrom`、`dateTo`；未传范围时使用 `date` 作为单日查询。
- `includeUnfinished=true` 时，除范围内记录外，补回截至结束日仍处于开放或待处理状态的事项。
- 工作台、消息通知或审批中心跳转必须保留日期范围、数据范围、客户和业务方式等筛选条件。

## SQL 与 Mapper

- 当前接口实现只使用 MyBatis-Plus Wrapper；禁止新增 SQL 注解 Mapper 和旧接口回退。
- 数据库耗时由 Druid 慢 SQL 统计记录，业务日志只记录安全的调用标识与耗时。
