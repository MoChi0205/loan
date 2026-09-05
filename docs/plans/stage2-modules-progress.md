# 阶段二开发进度归档：②审批 / ③短信 / ④报表 / ⑤工作台·配置·模板·初筛 / ⑥组织权限·黑名单

> 2026-08-26 ｜ 阶段二 Web 端全模块完成 ｜ 状态：✅ 后端编译通过、前端构建通过、本地运行中、接口全量 200

## 一、本期完成范围

### ② 审批半区（补全）
- **数据库**：`t_product_approval`（bank_product_id/approver_staff_id）、`t_attachment_download_approval`（applicant_staff_id/approver_staff_id）→ 业务编码列
- **后端** `com.loan.approval`：ProductApproval / AttachmentDownloadApproval 实体 + ApprovalService（产品审核通过联动产品入全量库；下载审批通过生成 24h 限时链接 token）+ ApprovalController
- **前端**：ApprovalCenter.vue（产品审核 + 下载审批双 tab）+ 发起申请
- 验证：申请 → 审批通过 → 生成 24h token ✅

### ③ 短信中心
- **数据库**：`t_sms_record.template_id` → `template_code`
- **后端** `com.loan.sms`：SmsAdminService（模板 CRUD / 记录分页 / 手动发送模拟通道）+ SmsAdminController（/api/admin/sms/*）
- **前端**：SmsCenter.vue（模板管理 + 发送记录 + 手动发送）
- 验证：模板列表 / 记录 / 手动发送 ✅

### ④ 报表中心
- **数据库**：`t_client_screening`（client_profile_id/match_trace_id/template_id）→ 业务编码/UUID
- **后端** `com.loan.report`：ReportService（经营总览 / 成交趋势 / 奖励趋势 / 初筛报告分页详情）+ ReportController
- **前端**：ReportCenter.vue（统计卡片 + 月度趋势表 + 报告列表/详情）
- 验证：总览（客户 2/线索 3/工单 1/成交 10 万/奖励 1 万）✅

### ⑤ 工作台 / 配置向导 / 报告模板 / 初筛
- **后端**：
  - `com.loan.dashboard`：DashboardService.todo（待审核产品/下载/奖励、我的工单、我的线索）
  - `com.loan.config`：ConfigService.status（8 项配置完成度）
  - `com.loan.report`：ReportTemplate 实体 + ReportTemplateService（模板 CRUD/发布停用）
  - `com.loan.screening`：ScreeningService.run（客户+经营事实 → 规则引擎匹配 → 落审计 → 生成初筛报告）+ ScreeningController
- **前端**：Workbench 增强（真实指标 + 待办事项）、ConfigurationWizard.vue（7 步向导+完成度）、ScreeningCenter.vue（初筛执行）、ReportTemplateList.vue（模板管理）
- 验证：初筛执行生成 reportb21f42...（LOW 档，引擎真实跑规则）✅

### ⑥ 组织权限写接口 + 黑名单
- **数据库**：`t_blacklist.release_staff_id` → `release_staff_code`
- **后端**：
  - `com.loan.blacklist`：Blacklist 实体 + BlacklistService（分页/新增全局生效/解禁仅老板）+ Controller
  - `com.loan.org`：OrgWriteService（部门增改停 / 员工增改离职 / 角色权限先删后插）+ OrgController 写接口
- **前端**：BlacklistCenter.vue + OrgCenter 重写（部门树管理/员工新增编辑离职/角色权限配置弹窗）
- 验证：黑名单新增/列表、员工新增、部门新增、角色权限保存 ✅（修复 Integer→Long ClassCastException）

## 二、本地运行状态

- 后端 `http://localhost:8080/loan`（直连 prd Nacos → 远程 MySQL/Redis），启动日志 `/tmp/loan-backend.log`
- 前端 `http://localhost:5173/`（vite dev，需用后台任务方式启动，nohup & 方式 proxy 会 502）
- 登录：crm-boss-001（任意密码）
- 已入库演示数据：奖励规则 V1、推荐人/被推荐人客户、邀请关系、已成交工单、已发放奖励、已生成初筛报告、黑名单示例、测试部门 TEST_DEPT、测试员工 ADV002

## 三、验证结论

- 后端：`mvn -pl loan-service -am compile -DskipTests` 多次编译通过（含 2 处修复：ReportService 泛型 sum、OrgController menuIds 类型转换）
- 前端：`vite build` 通过（修复 OrgCenter menuTree 重名、api/org 与 api/blacklist 导入拆分）
- 阶段二 Web 端 12 个模块接口抽查全部 200

## 四、遗留 / 后续

- 角色权限回显（编辑权限时按已授权 menuId 勾选）—— 前端 openPerm 简化处理，可后续增强
- 员工离职联动线索/客户转移提醒、部门审核人配置（t_dept_approver）—— 迭代增强
- 报告模板版本化发布流（多版本并存）—— 已支持，运营时启用
- 渠道端（t_product_approval 提交入口 / t_channel_user）—— 渠道端迭代
