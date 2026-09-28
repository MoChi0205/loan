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
| 服务运营 | `/api/admin/appointment`、`/api/admin/outing`、`/api/admin/workbench` | 预约、外出、每日名单和范围切换 |
| 审批 | `/api/admin/approval` | 我的申请、待审批、分配、下载等统一审批 |
| 手机号查看 | `/api/admin/lead/sensitive` | 解锁、额度、超限审批和留痕 |
| 报告 | `/api/admin/report`、`/api/mini/report` | 员工聚合报告与客户经营报告隔离 |
| 通知 | `/api/notification`、`/api/mini/notification` | 消息列表、未读数和已读状态 |

## 输出约束

- 客户端 DTO 不得出现银行产品、内部准入、渠道匹配或内部规则命中信息。
- 页面 DTO 返回名称和中文释义；内部编码仅用于请求定位时传输，不作为页面文案展示。
- 手机号列表字段只返回脱敏值；明文只由敏感查看接口按权限单次返回。
- 错误响应不得包含 SQL、密钥、手机号明文、token 或内部堆栈。
