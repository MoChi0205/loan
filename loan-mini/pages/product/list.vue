<template>
  <view class="product-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 状态说明：让渠道理解每个状态的含义与可做操作（C9） -->
    <view class="card legend-card">
      <text class="card-title">{{ isChannel ? '合作产品状态' : '银行产品状态' }}</text>
      <view v-for="(l, i) in legend" :key="i" class="legend-row">
        <AppTag :type="l.tone" size="sm">{{ l.label }}</AppTag>
        <text class="legend-desc">{{ l.desc }}</text>
      </view>
    </view>

    <!-- 骨架屏（P1-5 Loading 态） -->
    <AppSkeleton v-if="loading" :rows="3" />

    <!-- 空态 -->
    <!-- 加载失败（与空态分离） -->
    <AppEmpty v-if="hasError && !products.length" title="加载失败"
      desc="网络异常或服务暂不可用，请重试">
      <AppButton variant="primary" size="md" @click="load()">重试</AppButton>
    </AppEmpty>

    <AppEmpty v-else-if="!products.length" title="暂无产品" :desc="isChannel ? '录入第一笔合作产品，提交后由平台运营审批' : '当前暂无可管理的银行产品'">
      <AppButton variant="primary" size="md" @click="goEdit()">{{ isChannel ? '录入合作产品' : '录入银行产品' }}</AppButton>
    </AppEmpty>

    <!-- 产品列表 -->
    <view v-else class="stack">
      <view v-for="p in products" :key="p.code" class="card prod-card u-hover">
        <view class="prod-top">
          <text class="prod-name u-ellipsis">{{ p.productName || p.bankProductCode }}</text>
          <AppTag :type="statusTagType(p.status)" size="sm">{{ statusLabel(p.status) }}</AppTag>
        </view>

        <view class="prod-row">
          <text class="prod-label">所属银行</text>
          <text class="prod-value">{{ p.bankName || '—' }}</text>
        </view>
        <view class="prod-row">
          <text class="prod-label">额度区间</text>
          <text class="prod-value">{{ p.amountRange || '—' }}</text>
        </view>
        <view class="prod-row" v-if="p.cooperateUntil">
          <text class="prod-label">合作有效期至</text>
          <text class="prod-value">{{ p.cooperateUntil }}</text>
        </view>

        <!-- 驳回原因 -->
        <view v-if="p.status === 'REJECTED' && p.rejectReason" class="reject-box">
          驳回原因：{{ p.rejectReason }}
        </view>

        <!-- 待删除提示 -->
        <view v-if="p.status === 'PENDING_DELETE'" class="pending-box">
          待我司终审删除 · 审批通过后将<b>从全量库中移除</b>（操作留痕至审计日志）
        </view>

        <!-- 操作区（C9 状态机驱动，触控 44px） -->
        <view class="prod-actions" v-if="actionsOf(p).length">
          <AppButton
            v-for="(a, i) in actionsOf(p)" :key="i"
            :variant="a.variant" size="sm"
            :loading="!!(acting === p.code + a.key)"
            @click="onAction(p, a)"
          >{{ a.label }}</AppButton>
        </view>
      </view>
    </view>

    <!-- 底部录入入口 -->
    <view class="footer">
      <AppButton variant="primary" size="lg" block @click="goEdit()">{{ isChannel ? '录入合作产品' : '录入银行产品' }}</AppButton>
    </view>
  </view>

  <!-- 角色化底部导航（自绘 tabBar，渠道：首页 / 我的产品 / 我的） -->
  <TabBar v-if="isChannel" current="product" />
</template>

<script setup>
/**
 * 渠道「我的产品」（C9 撤销审批 + 申请删除 + 撤销删除）。
 *
 * 状态机：
 *   DRAFT ─提交审批→ PENDING ─运营通过→ OK ─申请删除→ PENDING_DELETE ─运营批准→ DELETED
 *     ↑               │（撤销审批）                        │（撤销删除 / 驳回 → OK）
 *     └───────────────┘                                    └──────────────────┘
 *
 * 可用操作由状态推导（actionsOf），新增状态只需改一处映射表。
 *
 * 无障碍（P1-3/P1-4）：所有按钮 min-height 88rpx（44px），
 * 由 AppButton 统一处理 hover / focus / disabled / loading 六态。
 */
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import TabBar from '../../components/TabBar.vue';
import { myProducts, submitProduct, revokeApproval, applyDelete, cancelDelete, PRODUCT_STATUS } from '../../api/product';
import { useUserStore } from '../../store/user';

const loading = ref(true);
const products = ref([]);
const hasError = ref(false);
/** 正在执行的操作（产品编码 + 操作 key），用于按钮级 loading */
const acting = ref('');
/** 用户状态（T3 · C 类：平板限宽标记 isTablet 驱动 u-shell） */
const store = useUserStore();
const isChannel = computed(() => store.isChannel);

/** 状态说明图例 */
const legend = [
  { label: '草稿', tone: 'muted', desc: '已保存未提交，可编辑或提交审批' },
  { label: '待审批', tone: 'warning', desc: '已提交，等待我司运营 / 超管终审，可撤销' },
  { label: '已上架', tone: 'success', desc: '审批通过，在合作库展示，可申请删除' },
  { label: '已驳回', tone: 'danger', desc: '未通过，可见原因，编辑后可重新提交' },
  { label: '待删除', tone: 'danger', desc: '已申请删除，等待终审，可撤销' },
];

function statusLabel(s) {
  return {
    [PRODUCT_STATUS.DRAFT]: '草稿',
    [PRODUCT_STATUS.PENDING]: '待审批',
    [PRODUCT_STATUS.OK]: '已上架',
    [PRODUCT_STATUS.REJECTED]: '已驳回',
    [PRODUCT_STATUS.PENDING_DELETE]: '待删除',
  }[s] || (s || '未知');
}

function statusTagType(s) {
  if (s === PRODUCT_STATUS.OK) return 'success';
  if (s === PRODUCT_STATUS.PENDING) return 'warning';
  if (s === PRODUCT_STATUS.REJECTED || s === PRODUCT_STATUS.PENDING_DELETE) return 'danger';
  return 'muted';
}

/**
 * 按状态推导可用操作（C9 状态机）。
 *
 * @param {Object} p 产品
 * @returns {Array<{key:string,label:string,variant:string,confirm?:string}>}
 */
function actionsOf(p) {
  const list = [];
  if (p.status === PRODUCT_STATUS.DRAFT) {
    list.push({ key: 'submit', label: '提交审批', variant: 'primary' });
    list.push({ key: 'edit', label: '编辑', variant: 'secondary' });
  } else if (p.status === PRODUCT_STATUS.PENDING) {
    list.push({ key: 'revoke', label: '撤销审批', variant: 'secondary' });
  } else if (p.status === PRODUCT_STATUS.OK) {
    list.push({ key: 'delete', label: '申请删除', variant: 'secondary', confirm: '申请删除后需我司运营 / 超管终审，审批通过将从全量库移除。确认申请？' });
  } else if (p.status === PRODUCT_STATUS.REJECTED) {
    list.push({ key: 'edit', label: '编辑重提', variant: 'primary' });
  } else if (p.status === PRODUCT_STATUS.PENDING_DELETE) {
    list.push({ key: 'cancelDelete', label: '撤销删除', variant: 'primary' });
  }
  return list;
}

async function onAction(p, action) {
  // 破坏性操作二次确认（删除申请）
  if (action.confirm) {
    const ok = await new Promise((resolve) => {
      uni.showModal({
        title: '确认操作',
        content: action.confirm,
        success: (res) => resolve(!!res.confirm),
        fail: () => resolve(false),
      });
    });
    if (!ok) return;
  }

  if (action.key === 'edit') { goEdit(p); return; }

  acting.value = p.code + action.key;
  try {
    if (action.key === 'submit') {
      await submitProduct(p.code);
      uni.showToast({ title: '已提交，进入待审批', icon: 'none' });
    } else if (action.key === 'revoke') {
      await revokeApproval(p.code);
      uni.showToast({ title: '已撤销审批，回到草稿', icon: 'none' });
    } else if (action.key === 'delete') {
      await applyDelete(p.code, '渠道主动申请下架');
      uni.showToast({ title: '已申请删除，待我司终审', icon: 'none' });
    } else if (action.key === 'cancelDelete') {
      await cancelDelete(p.code);
      uni.showToast({ title: '已撤销删除申请', icon: 'none' });
    }
    await load();
  } catch (e) { hasError.value = true; }
  finally { acting.value = ''; }
}

function goEdit(p) {
  const url = p && p.code
    ? `/pages/product/edit?code=${p.code}`
    : '/pages/product/edit';
  uni.navigateTo({ url });
}

async function load() {
  loading.value = true;
  hasError.value = false;
  try {
    const data = await myProducts();
    products.value = Array.isArray(data) ? data : [];
  } catch (e) {
    products.value = [];
    hasError.value = true;
  } finally {
    loading.value = false;
  }
}

onShow(() => {
  uni.setNavigationBarTitle({ title: isChannel.value ? '我的产品' : '产品中心' });
  load();
});
</script>

<style scoped>
.product-page {
  min-height: 100vh;
  padding: var(--space-4);
  background: var(--bg-page);
  box-sizing: border-box;
  /* P2-7：底部留白 ≥ 吸底按钮高度 + 安全区 */
  padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom));
}

.stack view { margin-top: var(--space-3); }
.stack view:first-child { margin-top: 0; }

.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  box-shadow: var(--shadow-md);
}
.card-title { font-size: var(--fs-lg); font-weight: 700; color: var(--text-primary); }

/* 状态说明 */
.legend-card { margin-bottom: var(--space-3); }
.legend-row {
  display: flex; align-items: center; gap: var(--space-2);
  padding: var(--space-2) 0; min-height: 64rpx;
}
.legend-desc { flex: 1; min-width: 0; font-size: var(--fs-sm); color: var(--text-secondary); }

/* 产品卡 */
.prod-top {
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-2); margin-bottom: var(--space-2);
}
.prod-name { flex: 1; min-width: 0; font-size: var(--fs-lg); font-weight: 700; color: var(--text-primary); }

.prod-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: var(--space-1) 0; min-height: 56rpx;
}
.prod-label { font-size: var(--fs-sm); color: var(--text-secondary); }
.prod-value { font-size: var(--fs-sm); color: var(--text-primary); font-weight: 600; }

.reject-box, .pending-box {
  margin-top: var(--space-2);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
}
.reject-box {
  background: rgba(239, 68, 68, .06);
  border: 2rpx solid rgba(239, 68, 68, .25);
  color: var(--danger-text);
}
.pending-box {
  background: rgba(245, 158, 11, .08);
  border: 2rpx solid rgba(245, 158, 11, .25);
  color: var(--warning-text);
}

.prod-actions {
  display: flex; flex-wrap: wrap; gap: var(--space-2);
  margin-top: var(--space-3);
}

.footer { margin-top: var(--space-4); }
</style>
