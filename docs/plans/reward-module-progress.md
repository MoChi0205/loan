# 阶段二开发进度归档：② 推荐奖励（reward）模块 + 本地运行

> 2026-08-26 ｜ 完成内容：reward 后端结算/审核 + 前端页面 + 端到端验证 + 本地前后端运行 ｜ 状态：✅ 编译/构建通过，本地可访问

## 一、本期完成范围

### 1. 数据库迁移（追加至 `db/migrate-bizid-fk.sql` 第 9/10 节，已执行）
- `t_reward_record`：`referrer_client_id/referee_client_id/service_order_id/settle_staff_id`（BIGINT）→ `referrer_client_code/referee_client_code/service_order_no/settle_staff_code`（VARCHAR），唯一索引 `uk_order_referrer_level` 重建
- `t_withdraw_record`：`reward_id` → `reward_no`
- 执行时均 0 行，无风险；全部执行成功

### 2. 后端：推荐奖励（`com.loan.reward`）
| 文件 | 说明 |
|------|------|
| `entity/RewardRecord` | 奖励流水实体（状态机 PENDING_AUDIT→GRANTED/REJECTED/VOID） |
| `entity/RewardRule` | 奖励规则实体（比例快照冻结） |
| `mapper/RewardRecordMapper` / `RewardRuleMapper` | MyBatis-Plus |
| `service/RewardService` | `settleForOrder`（工单 DEAL 自动结算：被推荐客户→邀请关系→推荐人→生效规则→直推奖励，幂等）/ `page`（含客户名）/ `audit`（发放/驳回）/ `voidReward`（作废） |
| `controller/RewardController` | GET `/api/admin/reward/page`、POST `/{rewardNo}/audit`、POST `/{rewardNo}/void` |
| `invitation/entity/Invitation` + Mapper | 邀请关系（内部多态引用，仅结算链路用） |

- **OrderService 集成**：DEAL 后自动调用 `rewardService.settleForOrder`（同一事务，幂等）

### 3. 前端：reward 页面
- `src/api/reward.js`：pageRewards / auditReward / voidReward
- `src/views/reward/RewardList.vue`：列表（推荐人/被推荐人/层级/基数/比例/金额/状态）+ 审核弹窗（发放/驳回意见必填）+ 作废
- 路由 `/reward` + 侧栏菜单「推荐奖励」

### 4. 本地运行（用户要求）
- **SQL 执行**：`migrate-bizid.sql`（11/11）与 `migrate-bizid-fk.sql`（10 节）全部执行成功（用户提供的 root 账号）
- **后端**：`bash scripts/run-dev-prd.sh` 直连 prd Nacos（124.221.150.239:9848）→ 远程 MySQL（110.42.219.5:9306）+ 远程 Redis（124.221.116.28:9379），`http://localhost:8080/loan` ✅
- **前端**：vite dev `http://localhost:5173/` ✅（登录：crm-boss-001 / 任意密码）
- **端到端验证**：登录 → 建单 → 开始服务 → 成交 10 万 → 自动生成奖励单 1 万元（10% 直推）→ 审核发放（结算人 BOSS001）✅

## 二、演示数据（已入库，便于页面查看）

| 数据 | 值 |
|------|-----|
| 奖励规则 V1 | direct_rate=0.1，min=1，max=50000，ACTIVE |
| 推荐人客户 | client917e39bdbad9033b03d09029c6589de1（推荐人科技公司/王推荐） |
| 被推荐人客户 | client6b3d8e5e705051eb0bc6d409f59c6e79（被推荐人贸易公司/李成交） |
| 邀请关系 | referrer_type=CUSTOMER，used_flag=1 |
| 已成交工单 | order31a0524565bc075e22112633ecf2d8d4（10 万，DEAL） |
| 已发放奖励 | reward35a19dadadacb21ebfba2443df1a10d7（10000 元，GRANTED） |

## 三、验证

- 后端 `mvn -pl loan-service -am compile -DskipTests` ✅；重启后 reward 接口可用
- 前端 `vite build` ✅
- 完整业务闭环（建单→服务→成交→奖励结算→审核发放）API 级验证通过

## 四、遗留 / 后续（阶段二③ 起）

- 资料审批（产品审核 t_product_approval / 附件下载审批 t_attachment_download_approval）—— ② 的审批半区，下一步做
- 奖励提现（t_withdraw_record）接入微信商家转账
- 间推（2 层）奖励：规则已预留 indirect_enabled 开关，默认关
- 线上 VIP 支付不计入奖励基数（base_caliber=SERVICE_ORDER_DEAL 已限定）
