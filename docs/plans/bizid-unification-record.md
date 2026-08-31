# 业务 ID 统一改造记录（Wave 1 + Wave 2）

> 2026-08-26 ｜ 状态：已完成并编译通过 ｜ 关联任务：#52 #53 #54 #55 #56

## 一、背景

业务唯一 ID 约定为「自定义前缀 + 32 位随机」（`BizIdGenerator.generate(prefix)`），
但大量接口仍以自增 `Long id` 作业务入参。本次按用户拍板边界执行：
**接口 + 存储全统一**——保留自增 Long 主键（不暴露），FK 引用列改 VARCHAR 存业务编码，不设物理外键。

## 二、Wave 1：接口身份统一（Rule / Product / Lead）

| 模块 | 改动 |
|------|------|
| Rule | `RuleController` get/delete 改 `@PathVariable String ruleCode`；create 返回 `Result<String>`；batch-status 用 `ruleCodes`；`RuleService` 新增 `getByCode/deleteByCode`；`RuleSaveReq` 删 `Long id` 留 `ruleCode` |
| Product | `ProductController` get/delete 改 `@PathVariable String productCode`；create 返回 productCode；`ProductService` 新增 `getByCode/deleteByCode`；`ProductSaveReq` 删 `Long id` 留 `productCode` |
| Lead | `LeadController` claim/assign 请求体 `{leadId,toStaffId}` → `{leadNo,toStaffCode}`；create 返回 leadNo；`LeadService.claim/assign/create` 按业务编码解析 |

## 三、Wave 2：FK 引用列编码化（6 张表 + Java 全链路）

迁移脚本：`db/migrate-bizid-fk.sql`（28 条语句全部执行成功，数据回填验证通过，旧列 0 残留）。

| 表 | 旧列（已删） | 新列 |
|----|-------------|------|
| t_lead | recorder_staff_id / owner_staff_id / client_profile_id | recorder_staff_code / owner_staff_code / client_profile_code |
| t_lead_allocation_record | lead_id / from_staff_id / to_staff_id | lead_no / from_staff_code / to_staff_code |
| t_staff | dept_id | dept_code |
| t_department | parent_id / leader_staff_id | parent_code / leader_staff_code |
| t_service_attachment | order_id / client_profile_id | order_no / client_profile_code |
| t_bank_product | bank_channel_id | bank_channel_code |

Java 改造文件（14 个）：
- 实体：`Lead` / `LeadAllocationRecord` / `Staff` / `Department` / `ServiceAttachment` / `BankProduct` / `LoanUser`（deptId→deptCode）/ `MenuNodeVO`（新增 parentCode）
- 服务：`LeadService`（create/claim/assign/recycle/warnRecycle/buildRecord 全编码化，删除 resolveStaff/staffNo）、`OrgService`（部门树按 parentCode 串联、pageStaff 按 deptCode）、`AttachmentService`（page 按 clientProfileCode/orderNo）、`ProductService`（resolveChannel 返回 channelCode）、`ProductQueryService`（渠道批量查询按 channelCode）、`AuthService`（setDeptCode）
- 控制器：`LeadController`（create 传 userNo）、`OrgController`（staffPage deptCode）、`AttachmentController`（page 参数改编码）
- 契约：`ProductDTO`（bankChannelId→bankChannelCode）
- 基础设施：`CurrentUserAspect`（deptCode case）

## 四、规范文档

- 新建 `docs/skills/business-id/SKILL.md`（业务 ID + FK 引用列规范，含自检清单）
- 更新 `docs/业务ID规范.md` → v1.1（新增「四·五 FK 引用列统一为业务编码」）。**已执行动作记录**：该 md 现已归档为 `docs/plans/archive/业务ID规范.md`（仅历史留痕），规范真源并入 `.workbuddy/skills/loan-biz-id/SKILL.md`
- 更新 `docs/skills/backend-development/SKILL.md`（自检清单 + 链接 business-id）

## 五、前端同步

- `OrgCenter.vue`：员工筛选 `deptId` → `deptCode`，部门树点击改用 `data.code`（deptCode）

## 六、验证

- `mvn -pl loan-service -am compile -DskipTests`（JDK 1.8.0_202）两次均通过（Wave 1、Wave 2）
- 数据库迁移 28/28 成功；回填抽查：lead 归属人 BOSS001、staff 部门 BOSS_DIRECT/CONSULT、产品渠道 WH_BANK/HB_BANK 全部正确；旧 BIGINT 列 0 残留

## 七、遗留（后续迭代）

- `t_lead_ent_ext / t_lead_person_ext / t_client_profile / t_service_follow` 等表仍以自增主键做内部关联（无 Java 使用、不外露，保留可接受）
- `t_lead_archive` 仍为 BIGINT 归属列（无实体，归档功能开发时按本规范处理）
- 旧 Redis 会话中的 `deptId` 字段：重新登录即刷新
