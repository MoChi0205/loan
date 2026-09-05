# 阶段三 小程序后端接口完成（登录/邀请/OCR认证/匹配/报告/线索/服务单/推荐/企微）

## 交付接口（16 个，全部经网关鉴权自动登记，CUSTOMER 类型用户专属）

| 分组 | 接口 | 说明 |
|------|------|------|
| 登录 | POST `/api/mini/auth/login` | 手机号+短信验证码登录（白名单），首登自动建客户档案，可带邀请码绑定 |
| 我的 | GET `/api/mini/me` | 客户资料摘要 |
| 企业认证 | POST `/api/mini/auth/enterprise` | 营业执照信息认证（信用代码唯一性校验，customerGroup→ENTERPRISE） |
| 邀请绑定 | POST `/api/mini/invitation/bind` | 绑定邀请码（校验 ACTIVE/未用/未过期/非本人，一次性） |
| 邀请 | GET `/api/mini/invitation/mine` | 我的专属邀请码（幂等生成，7 天） |
| 邀请 | GET `/api/mini/invitation/records` | 我的邀请记录 |
| 匹配 | POST `/api/mini/match/run` | 提交经营事实→引擎匹配→生成报告（复用 ScreeningService） |
| 匹配 | GET `/api/mini/match/history` | 我的匹配历史 |
| 报告 | GET `/api/mini/report/list` / `/{reportNo}` | 我的报告列表 / 详情（校验归属） |
| 线索 | POST `/api/mini/lead/submit` | 融资需求录入（MINI 来源进公海） |
| 服务单 | GET `/api/mini/order/list` / `/{orderNo}` | 我的服务单列表 / 详情（校验归属） |
| 推荐有礼 | GET `/api/mini/reward/mine/summary` / `/mine` | 推荐奖励汇总 / 记录 |
| 企微活码 | GET `/api/mini/wecom/qrcode` | 企微客服活码（t_config WECOM 组可后台配置） |

## 权限模型（网关三维扩展）
- **用户类型维度新增**：规则下发 `typeRules: {"CUSTOMER":["mini:"], "CHANNEL":["channel:"]}`——无角色用户（客户/渠道）只能访问对应前缀接口，**客户访问 admin 接口 403**，员工角色无法访问 mini 接口
- 网关白名单新增：`/api/mini/auth/login`（登录前）、`/api/sms/send-code`（验证码获取）
- mini 接口默认 `WEB,MINI_APP` 双端，管理端可配置端限制

## 数据库迁移（已执行，追加 db/migrate-bizid-fk.sql 第 14 节）
- `t_client_submission.client_profile_id` → `client_profile_code`
- `t_client_business_fact.client_profile_id/submission_id` → `client_profile_code/submission_no`
- `t_invitation` 新增 `referrer_client_code` / `used_by_client_code`
- `t_config` 新增 WECOM.qrcode 企微活码种子

## 端到端验证（全部经网关 8088，X-Client-Type: MINI_APP）
- 客户登录 ✅（token userType=CUSTOMER）
- 客户访问 7 类 mini 接口全 200 ✅；越权 admin/order/page → 403 ✅；BOSS 访问 mini → 200 ✅
- 企业认证 ✅ → 匹配生成报告 ✅（引擎真实跑规则 grade=LOW/prod=2）→ 报告详情 ✅
- 线索录入 ✅（ext_json JSON 转义修复后）→ 我的邀请码 ✅ → 客户 B 绑定邀请码 ✅（referrerType=CUSTOMER）→ 重复绑定报「已被使用」✅

## 关键文件
- `loan-service/.../mini/`（MiniAuth/MiniInvitation/MiniMatch/MiniLead/MiniOrder/MiniReward 6 组 controller+service）
- `loan-service/.../invitation/service/InvitationService.java`（新建）
- `loan-service/.../config/entity/ConfigItem.java` + `config/mapper/ConfigItemMapper.java`（新建）
- `AuthService.customerLogin`（新增）
- 网关 `ApiAuthGlobalFilter`（typeRules + 白名单）、`ApiPermissionService.buildRules`（typeRules 下发）

## 遗留与建议
- OCR 识别真实通道（当前 field-defs/record 为落库骨架，识别能力待接）
- 小程序前端（loan-mini 骨架）按上述接口开发，请求注入 `X-Client-Type: MINI_APP`
- 企业认证可扩展证件图片上传（走 OSS + OcrService）
