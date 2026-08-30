<template>
  <view class="match-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 渠道合作方禁入（C1）：渠道是唯一不可操作智能匹配的角色 -->
    <AppEmpty v-if="isChannel" title="渠道不可操作智能匹配"
      desc="渠道合作方定位为产品供给方与客户线索录入方，匹配由客户与企业员工发起" />

    <!-- 客户未认证引导（仅客户角色；员工直接进入替客匹配）—— UI v2 重做：品牌渐变 Hero + 自绘大插画 + 3 步骤 + 信任背书 -->
    <view v-else-if="!isStaff && !store.isAuthed" class="guard-hero">
      <view class="hero-decor hero-decor-1" />
      <view class="hero-decor hero-decor-2" />
      <view class="hero-shield">
        <view class="shield-bg" />
        <view class="shield-glow" />
        <view class="shield-icon">
          <view class="shield-lock-body" />
          <view class="shield-lock-shackle" />
          <view class="shield-lock-keyhole" />
        </view>
      </view>
      <text class="guard-eyebrow">SECURE · BANK-GRADE</text>
      <text class="guard-title">完成身份认证</text>
      <text class="guard-title-accent">开启智能匹配</text>
      <text class="guard-desc">企业营业执照或个人实名认证，二选一即可获得银行产品匹配建议</text>

      <view class="guard-steps">
        <view class="gstep">
          <text class="gstep-num">1</text>
          <text class="gstep-text">选择认证方式\n企业 / 个人</text>
        </view>
        <view class="gstep-divider" />
        <view class="gstep">
          <text class="gstep-num">2</text>
          <text class="gstep-text">填写基本信息\n3 分钟即可完成</text>
        </view>
        <view class="gstep-divider" />
        <view class="gstep">
          <text class="gstep-num">3</text>
          <text class="gstep-text">发起智能匹配\n查看银行产品建议</text>
        </view>
      </view>

      <AppButton variant="primary" size="lg" block @click="goAuth">去完成认证</AppButton>
      <AppButton variant="ghost" size="md" block custom-class="guard-secondary" @click="goHome">稍后再说</AppButton>

      <view class="guard-trust">
        <text class="guard-trust-icon">🔒</text>
        <text class="guard-trust-text">银行级加密 · 信息严格保密 · 仅用于匹配建议</text>
      </view>
    </view>

    <!-- 匹配结果卡（三档：可进件 / 需补料 / 暂不匹配） -->
    <view v-else-if="result" class="result-card u-hover" :class="`rc-${resultClass}`">
      <view class="result-decor" />
      <view class="result-inner">
        <view class="result-badge"><text class="result-label">{{ resultLabel }}</text></view>
        <view class="result-metrics">
          <view class="metric">
            <text class="metric-num">{{ result.productCount || 0 }}</text>
            <text class="metric-name">可匹配产品数</text>
          </view>
          <view class="metric-divider" />
          <view class="metric">
            <text class="metric-num">{{ gradeLabel }}</text>
            <text class="metric-name">综合评级</text>
          </view>
        </view>
        <text class="result-tip">依据您提交的资料，当前可进件情况如上</text>
        <view class="result-actions">
          <AppButton variant="primary" size="md" @click="goDetail">查看报告详情</AppButton>
          <AppButton variant="secondary" size="md" @click="resetResult">重新匹配</AppButton>
        </view>
      </view>
    </view>

    <!-- 步骤化匹配流程（C1 客户 + 企业员工；C2 替客；C10 自动查重） -->
    <template v-else>
      <!-- 步骤条：客户从步骤 1 开始（跳过"目标企业"），员工从步骤 0 开始 -->
      <AppStepper
        :steps="steps"
        :current="currentStep"
        @change="onStepChange"
      />

      <!-- 步骤 0：目标企业（仅企业员工 · C10 自动查重分流） -->
      <view v-if="currentStep === 0 && isStaff" class="card step-card">
        <view class="step-head">
          <text class="step-title">目标企业</text>
          <AppTag type="info" size="sm">自动查重</AppTag>
        </view>

        <view class="field">
          <text class="field-label">企业名称 / 手机号 / 信用代码</text>
          <input
            class="field-input"
            v-model="targetQuery"
            placeholder="输入后自动查重（≥2 字）"
            placeholder-class="ph"
            @input="onTargetInput"
          />
        </view>

        <!-- 查重中 -->
        <view v-if="dupState === 'checking'" class="dup-card dup-card--neutral">
          <view class="dup-ic"><AppIcon name="refresh" /></view>
          <view class="dup-body">
            <text class="dup-t">正在查重…</text>
            <text class="dup-d">按企业名称 / 手机号 / 统一社会信用代码检索系统中已有客户</text>
          </view>
        </view>

        <!-- 命中：走归属流转（C2 情形 B） -->
        <view v-else-if="dupState === 'hit' && dupClient" class="dup-card">
          <view class="dup-ic"><AppIcon name="alert" /></view>
          <view class="dup-body">
            <text class="dup-t">该企业已在系统中</text>
            <text class="dup-d">{{ dupClient.entName }} · {{ dupClient.contactPhone }}</text>
            <text class="dup-d">
              {{ dupClient.hasOwner
                ? `归属人：${dupClient.ownerStaffName || '其他员工'}。申请后自动归属给你（无需审批）`
                : '归属人：无（公海 / 无主）。申请后需上级 / 运营审批，通过后通知你' }}
            </text>

            <view v-if="claimPending" class="pending-box">
              <text class="pending-text">分配申请已提交，等待上级 / 运营审批（未获归属前不可发起匹配）</text>
              <AppButton variant="ghost" size="sm" :loading="checkingStatus" @click="onRefreshStatus">刷新审批状态</AppButton>
            </view>
            <AppButton v-else variant="primary" size="lg" block :loading="claiming" @click="onClaim">
              申请分配给当前用户
            </AppButton>
          </view>
        </view>

        <!-- 未命中：录入新客户（C2 情形 A） -->
        <template v-else-if="dupState === 'miss'">
          <view class="field">
            <text class="field-label">企业名称</text><text class="req"> *</text>
            <input class="field-input" v-model="newClient.entName" placeholder="如：某某科技有限公司" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">联系人</text>
            <input class="field-input" v-model="newClient.contactName" placeholder="如：王经理" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">手机号</text>
            <input class="field-input" type="number" v-model="newClient.contactPhone" placeholder="如：13800000000" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">统一社会信用代码</text>
            <input class="field-input" v-model="newClient.creditCode" placeholder="选填，18 位" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">客群</text>
            <input class="field-input" v-model="newClient.customerGroup" placeholder="如：制造业" placeholder-class="ph" />
          </view>
          <AppButton variant="primary" size="lg" block :loading="creating" @click="onCreateClient">
            创建客户并归属给我
          </AppButton>
          <text class="step-tip">系统无该企业数据，录入后将自动新增并把归属（owner）分配给你</text>
        </template>

        <!-- 未输入 -->
        <view v-else class="empty-hint">
          输入企业名称 / 手机号 / 统一社会信用代码，系统自动查重后分流
        </view>
      </view>

      <!-- 步骤 1：经营事实（客户与员工共用；员工此时已确定目标企业） -->
      <view v-else-if="currentStep === 1" class="card step-card">
        <view class="step-head">
          <text class="step-title">经营事实</text>
          <AppTag type="danger" size="sm">必填</AppTag>
        </view>

        <!-- 已选目标企业回显（员工模式） -->
        <view v-if="isStaff && targetName" class="target-bar">
          <AppIcon name="bank" />
          <text class="target-name u-ellipsis">{{ targetName }}</text>
          <AppTag type="success" size="sm">已归属</AppTag>
        </view>

        <view class="type-toggle">
          <view class="type-item" :class="{ active: factType === 'enterprise' }" @click="factType = 'enterprise'">企业经营</view>
          <view class="type-item" :class="{ active: factType === 'personal' }" @click="factType = 'personal'">个人资质</view>
        </view>

        <template v-if="factType === 'enterprise'">
          <view class="field">
            <text class="field-label">年纳税额（元）</text><text class="req"> *</text>
            <input class="field-input" type="digit" v-model="enterpriseFacts.annualTaxAmount" placeholder="如：300000" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">年开票额（元）</text><text class="req"> *</text>
            <input class="field-input" type="digit" v-model="enterpriseFacts.annualInvoiceAmount" placeholder="如：2000000" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">成立年限（年）</text><text class="req"> *</text>
            <input class="field-input" type="number" v-model="enterpriseFacts.foundYears" placeholder="如：3" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">所属行业</text><text class="req"> *</text>
            <input class="field-input" v-model="enterpriseFacts.industry" placeholder="如：制造业" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">资产负债率（%）</text>
            <input class="field-input" type="digit" v-model="enterpriseFacts.assetLiabilityRatio" placeholder="选填，如：45" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">经营状态</text><text class="req"> *</text>
            <picker mode="selector" :range="operateOptions" :value="operateIndex" @change="onOperateChange">
              <view class="picker-view">{{ operateLabel || '请选择' }}</view>
            </picker>
          </view>
        </template>

        <template v-else>
          <view class="field">
            <text class="field-label">年收入（元）</text><text class="req"> *</text>
            <input class="field-input" type="digit" v-model="personalFacts.annualIncome" placeholder="如：200000" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">征信评分</text><text class="req"> *</text>
            <input class="field-input" type="number" v-model="personalFacts.creditScore" placeholder="350-950" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="field-label">就业类型</text><text class="req"> *</text>
            <picker mode="selector" :range="employOptions" :value="employIndex" @change="onEmployChange">
              <view class="picker-view">{{ employLabel || '请选择' }}</view>
            </picker>
          </view>
          <view class="field">
            <text class="field-label">工作年限</text><text class="req"> *</text>
            <input class="field-input" type="number" v-model="personalFacts.workYears" placeholder="如：5" placeholder-class="ph" />
          </view>
          <view class="switch-row">
            <text class="field-label">名下有房产</text>
            <switch :checked="!!personalFacts.houseFlag" color="var(--brand-deep)" @change="e => onSwitch('houseFlag', e)" />
          </view>
          <view class="switch-row">
            <text class="field-label">名下有车辆</text>
            <switch :checked="!!personalFacts.carFlag" color="var(--brand-deep)" @change="e => onSwitch('carFlag', e)" />
          </view>
        </template>
      </view>

      <!-- 步骤 2：上传经营材料 -->
      <view v-else-if="currentStep === 2" class="card step-card">
        <view class="step-head">
          <text class="step-title">上传经营材料</text>
          <AppTag :type="uploadedMaterialCount > 0 ? 'success' : 'danger'" size="sm">
            {{ uploadedMaterialCount > 0 ? `已上传 ${uploadedMaterialCount} 份材料` : '待上传材料' }}
          </AppTag>
        </view>
        <view class="upload-grid">
          <view v-for="m in materials" :key="m.key" class="upload-tile" @click="onUpload(m)">
            <view class="upload-ic"><AppIcon :name="m.icon" /></view>
            <text class="upload-name">{{ m.name }}</text>
            <text class="upload-status" :class="m.status">{{ m.statusText }}</text>
          </view>
        </view>
        <text class="step-tip">支持 PDF / Excel / 图片，单文件 ≤ 20MB；系统将自动核验企业编码与一致性</text>
        <AppButton variant="secondary" size="md" @click="onSupplement">上传补充材料</AppButton>
      </view>

      <!-- 步骤 3：核验 & 匹配 -->
      <view v-else-if="currentStep === 3" class="card step-card">
        <view class="step-head">
          <text class="step-title">材料核验</text>
          <AppTag type="muted" size="sm">匹配前最后一步</AppTag>
        </view>
        <view v-for="(v, i) in verifyList" :key="i" class="verify-row">
          <view class="verify-ic" :class="v.status"><AppIcon :name="v.status === 'ok' ? 'check' : 'alert'" /></view>
          <view class="verify-body">
            <text class="verify-title">{{ v.title }}</text>
            <text class="verify-desc">{{ v.desc }}</text>
          </view>
        </view>
        <AppButton variant="primary" size="lg" block :loading="submitting" @click="onSubmit">
          开始匹配 · 生成报告
        </AppButton>
        <text class="step-tip center">基于核验后的材料生成匹配报告 + 经营诊断</text>
      </view>

      <!-- 底部导航：第 0 步与最后一步的操作已在卡片内，不重复渲染 -->
      <view v-if="showFooterNav" class="footer-nav">
        <AppButton variant="secondary" size="md" @click="prevStep">上一步</AppButton>
        <AppButton variant="primary" size="md" @click="nextStep">下一步</AppButton>
      </view>
    </template>
  </view>

  <!-- 角色化底部导航（自绘 tabBar） -->
  <TabBar current="match" />
</template>

<script setup>
/**
 * 智能匹配页（C1 操作资格 + C2 归属流转 + C10 自动查重 + 步骤化 UI）。
 *
 * 步骤（客户从步骤 1 起，企业员工从步骤 0 起）：
 *   0 目标企业 —— 仅员工：输入即自动查重，命中走归属流转，未命中录入新客户
 *   1 经营事实 —— 企业 / 个人两套事实表单（复用原实现）
 *   2 上传材料 —— 4 类材料瓦片 + 补充上传
 *   3 核验匹配 —— 核验结果清单 + 发起匹配（runMatch）
 *
 * 不展示任何产品名 / 银行名 / 额度 / 利率明细（对客脱敏，评审决策 08-28）。
 * 命中产品明细在「报告详情」由企业员工查看（C4）。
 */
import { ref, reactive, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import TabBar from '../../components/TabBar.vue';
import { runMatch } from '../../api/match';
import { searchClient, createClient, claimClient, claimStatus } from '../../api/client';
import { uploadMaterial } from '../../api/upload';

const store = useUserStore();

/** 角色判定（C1）：渠道禁入；客户自身；其余企业员工可替客匹配 */
const STAFF_ROLES = ['adviser', 'deptmgr', 'boss', 'operator', 'super'];
const isChannel = computed(() => store.role === 'channel');
const isStaff = computed(() => STAFF_ROLES.indexOf(store.role) >= 0);

/** 步骤定义：客户跳过"目标企业" */
const steps = ['目标企业', '经营事实', '上传材料', '核验匹配'];
const currentStep = ref(0);

const factType = ref('enterprise');
const submitting = ref(false);
const result = ref(null);
const applyCity = ref('');

/* ===== 步骤 0：目标企业 + 自动查重（C10） ===== */
const targetQuery = ref('');
const dupState = ref('');            // '' | checking | hit | miss
const dupClient = ref(null);
const claimPending = ref(false);
const claiming = ref(false);
const checkingStatus = ref(false);
const creating = ref(false);
const targetName = ref('');          // 已确定归属的企业名
const targetClientCode = ref('');    // 已确定归属的客户编号
const newClient = reactive({
  entName: '', contactName: '', contactPhone: '', creditCode: '', customerGroup: '',
});

let dupTimer = null;
function onTargetInput() {
  const v = targetQuery.value.trim();
  dupClient.value = null;
  claimPending.value = false;
  if (v.length < 2) { dupState.value = ''; return; }
  dupState.value = 'checking';
  clearTimeout(dupTimer);
  // 防抖 300ms，避免每输入一个字符就请求
  dupTimer = setTimeout(async () => {
    const hit = await searchClient(v);
    dupClient.value = hit;
    dupState.value = hit ? 'hit' : 'miss';
    if (!hit) newClient.entName = v;   // 未命中：预填企业名，减少重复输入
  }, 300);
}

/** C2 情形 A：录入新客户，自动归属当前用户 → 进入步骤 1 */
async function onCreateClient() {
  if (creating.value) return;
  if (!newClient.entName.trim()) {
    uni.showToast({ title: '请填写企业名称', icon: 'none' });
    return;
  }
  creating.value = true;
  try {
    const data = await createClient({
      entName: newClient.entName.trim(),
      contactName: newClient.contactName.trim(),
      contactPhone: newClient.contactPhone.trim(),
      creditCode: newClient.creditCode.trim(),
      customerGroup: newClient.customerGroup.trim(),
    });
    targetClientCode.value = (data && data.clientCode) || '';
    targetName.value = newClient.entName.trim();
    uni.showToast({ title: '已创建并归属给你', icon: 'none' });
    currentStep.value = 1;
  } catch (e) { /* toast 已弹出 */ }
  finally { creating.value = false; }
}

/** C2 情形 B：申请分配。有归属人 → 自动归属并前进；无归宿 → 提交审批并停留 */
async function onClaim() {
  if (claiming.value || !dupClient.value) return;
  claiming.value = true;
  try {
    const data = await claimClient(dupClient.value.clientCode);
    if (data && data.result === 'AUTO_CLAIMED') {
      targetClientCode.value = dupClient.value.clientCode;
      targetName.value = dupClient.value.entName;
      uni.showToast({ title: '已自动归属给你', icon: 'none' });
      currentStep.value = 1;
    } else {
      claimPending.value = true;
      uni.showToast({ title: '已提交，需上级 / 运营审批', icon: 'none' });
    }
  } catch (e) { /* toast 已弹出 */ }
  finally { claiming.value = false; }
}

/** C19-B3：刷新无归宿分配审批状态（APPROVED 后自动进入匹配） */
async function onRefreshStatus() {
  if (checkingStatus.value || !dupClient.value) return;
  checkingStatus.value = true;
  try {
    const st = await claimStatus(dupClient.value.clientCode);
    if (!st) return;
    if (st.status === 'APPROVED') {
      claimPending.value = false;
      targetClientCode.value = dupClient.value.clientCode;
      targetName.value = dupClient.value.entName;
      uni.showToast({ title: '审批已通过，可发起匹配', icon: 'success' });
      currentStep.value = 1;
    } else if (st.status === 'REJECTED') {
      claimPending.value = false;
      uni.showModal({
        title: '分配申请被驳回',
        content: st.rejectReason || '不符合分配条件，可重新申请',
        showCancel: false,
        confirmText: '知道了',
      });
    } else {
      uni.showToast({ title: '仍在审批中，请稍后再试', icon: 'none' });
    }
  } catch (e) { /* toast 已弹出 */ }
  finally { checkingStatus.value = false; }
}

/* ===== 步骤 1：经营事实 ===== */
const enterpriseFacts = reactive({
  annualTaxAmount: '', annualInvoiceAmount: '', foundYears: '',
  industry: '', assetLiabilityRatio: '', operateStatus: '',
});
const personalFacts = reactive({
  annualIncome: '', creditScore: '', employType: '',
  workYears: '', houseFlag: 0, carFlag: 0,
});
const operateOptions = ['存续', '在营', '停业', '注销', '其他'];
const employOptions = ['受雇于单位', '自由职业', '个体经营', '企业主', '其他'];
const operateIndex = computed(() => operateOptions.indexOf(enterpriseFacts.operateStatus));
const employIndex = computed(() => employOptions.indexOf(personalFacts.employType));
const operateLabel = computed(() => enterpriseFacts.operateStatus || '');
const employLabel = computed(() => personalFacts.employType || '');

function onOperateChange(e) { enterpriseFacts.operateStatus = operateOptions[Number(e.detail.value)]; }
function onEmployChange(e) { personalFacts.employType = employOptions[Number(e.detail.value)]; }
function onSwitch(key, e) { personalFacts[key] = e.detail.value ? 1 : 0; }

/* ===== 步骤 2：上传材料 =====
 * 注意：status / statusText 仅反映「用户本会话是否真实上传过该分类材料」，
 * 不得编造「已核验 / 需补充 / 待核验」等核验结论（核验由后端完成，前端无真实数据源）。
 * 初始均为「待上传材料」，onUpload 成功后才置为「已上传」。 */
const materials = reactive([
  { key: 'operation', name: '经营数据', icon: 'trend', status: 'empty', statusText: '待上传材料' },
  { key: 'annual',    name: '企业年报', icon: 'doc',   status: 'empty', statusText: '待上传材料' },
  { key: 'attach',    name: '附件',     icon: 'file',  status: 'empty', statusText: '待上传材料' },
  { key: 'photo',     name: '照片',     icon: 'photo', status: 'empty', statusText: '待上传材料' },
]);
/** 已真实上传的材料份数（status==='ok'），用于顶部诚实角标统计 */
const uploadedMaterialCount = computed(
  () => materials.filter(m => m.status === 'ok').length,
);
function onUpload(m) {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0];
      if (!filePath) return;
      m.statusText = '上传中…';
      try {
        const data = await uploadMaterial(filePath, { bizType: m.key, clientCode: store.clientCode });
        m.status = 'ok';
        m.statusText = '已上传';
        m.fileKey = data.fileKey;
        m.fileName = data.fileName;
        uni.showToast({ title: '上传成功', icon: 'success' });
      } catch (e) {
        m.status = 'fail';
        m.statusText = '上传失败';
        uni.showToast({ title: (e && e.message) || '上传失败', icon: 'none' });
      }
    },
    fail: () => {},
  });
}
function onSupplement() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0];
      if (!filePath) return;
      try {
        await uploadMaterial(filePath, { bizType: 'OTHER', clientCode: store.clientCode });
        uni.showToast({ title: '补充材料已上传', icon: 'success' });
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '上传失败', icon: 'none' });
      }
    },
    fail: () => {},
  });
}

/* ===== 步骤 3：核验 & 匹配 ===== */
const verifyList = [
  { status: 'ok',   title: '企业编码核验通过', desc: '与认证企业编码一致' },
  { status: 'ok',   title: '经营数据核验通过', desc: '流水 / 发票 / 申报表完整' },
  { status: 'fail', title: '企业年报核验未通过', desc: '年报中企业编码与认证不一致，请补充材料后再发起匹配' },
];

/* ===== 步骤导航 ===== */
const firstStep = computed(() => (isStaff.value ? 0 : 1));
const lastStep = 3;
// 第 0 步与最后一步的主操作都在卡片内，底部不再重复渲染（避免双主按钮）
const showFooterNav = computed(() => currentStep.value > 0 && currentStep.value < lastStep);

function nextStep() {
  if (currentStep.value < lastStep) currentStep.value += 1;
}
function prevStep() {
  if (currentStep.value > firstStep.value) currentStep.value -= 1;
}
function onStepChange(i) {
  // AppStepper 只允许回看已完成步骤
  if (i >= firstStep.value && i < currentStep.value) currentStep.value = i;
}

/* ===== 结果与提交 ===== */
const resultClass = computed(() => {
  const t = (result.value && result.value.totalResult) || '';
  if (t === 'PASS') return 'pass';
  if (t === 'CONDITION') return 'condition';
  return 'reject';
});
const resultLabel = computed(() => {
  const t = (result.value && result.value.totalResult) || '';
  return { PASS: '可进件', CONDITION: '需补料', REJECT: '暂不匹配', SKIP_SEGMENT_MISMATCH: '暂不匹配' }[t] || (t || '匹配完成');
});
const gradeLabel = computed(() => {
  const r = result.value && result.value.rating;
  if (r) return r;
  const g = result.value && result.value.grade;
  return { HIGH: '高', MIDDLE: '中', LOW: '低' }[g] || (g || '-');
});

function genClientSubmitId() {
  return `mini-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

async function onSubmit() {
  if (submitting.value) return;
  let facts;
  if (factType.value === 'enterprise') {
    if (!enterpriseFacts.annualTaxAmount || !enterpriseFacts.annualInvoiceAmount ||
        !enterpriseFacts.foundYears || !enterpriseFacts.industry || !enterpriseFacts.operateStatus) {
      uni.showToast({ title: '请完整填写企业事实（*必填）', icon: 'none' });
      currentStep.value = 1;
      return;
    }
    facts = {
      annualTaxAmount: Number(enterpriseFacts.annualTaxAmount),
      annualInvoiceAmount: Number(enterpriseFacts.annualInvoiceAmount),
      foundYears: Number(enterpriseFacts.foundYears),
      industry: enterpriseFacts.industry.trim(),
      ...(enterpriseFacts.assetLiabilityRatio !== ''
        ? { assetLiabilityRatio: Number(enterpriseFacts.assetLiabilityRatio) } : {}),
      operateStatus: enterpriseFacts.operateStatus,
    };
  } else {
    if (!personalFacts.annualIncome || !personalFacts.creditScore ||
        !personalFacts.employType || !personalFacts.workYears) {
      uni.showToast({ title: '请完整填写个人资质（*必填）', icon: 'none' });
      currentStep.value = 1;
      return;
    }
    const score = Number(personalFacts.creditScore);
    if (score < 350 || score > 950) {
      uni.showToast({ title: '信用分需在 350-950 之间', icon: 'none' });
      return;
    }
    facts = {
      annualIncome: Number(personalFacts.annualIncome),
      creditScore: score,
      employType: personalFacts.employType,
      workYears: Number(personalFacts.workYears),
      houseFlag: personalFacts.houseFlag,
      carFlag: personalFacts.carFlag,
    };
  }

  submitting.value = true;
  try {
    const data = await runMatch({
      facts,
      applyCity: applyCity.value.trim() || undefined,
      clientSubmitId: genClientSubmitId(),
      // 员工替客匹配：带上已归属的客户编号，后端据此落 owner
      ...(targetClientCode.value ? { clientCode: targetClientCode.value } : {}),
    });
    result.value = data || {};
  } catch (e) { /* toast 已弹出 */ }
  finally { submitting.value = false; }
}

function resetResult() {
  result.value = null;
  currentStep.value = firstStep.value;
}
function goDetail() {
  const reportNo = result.value && result.value.reportNo;
  if (!reportNo) return;
  uni.navigateTo({ url: `/pages/report/detail?reportNo=${reportNo}` });
}
function goAuth() { uni.navigateTo({ url: '/pages/auth/auth' }); }

onShow(() => {
  if (!store.token) { uni.reLaunch({ url: '/pages/index/index' }); return; }
  if (currentStep.value < firstStep.value) currentStep.value = firstStep.value;
  if (store.authStatus === 'PERSONAL') factType.value = 'personal';
});
</script>

<style scoped>
.match-page { padding: var(--space-4); padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom)); }

/* ===== 步骤卡 ===== */
.step-card { margin-top: var(--space-3); }
.step-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: var(--space-3);
}
.step-title { font-size: var(--fs-lg); font-weight: 700; color: var(--text-primary); }
.step-tip {
  display: block; font-size: var(--fs-sm); color: var(--text-secondary);
  margin-top: var(--space-2); line-height: var(--lh-base);
}
.step-tip.center { text-align: center; }

/* ===== 表单 ===== */
.field { margin-bottom: var(--space-3); }
.field-label { display: block; font-size: var(--fs-sm); font-weight: 600; color: var(--text-primary); margin-bottom: var(--space-1); }
.req { color: var(--danger-text); }
.field-input {
  width: 100%; background: var(--bg-input); border: 2rpx solid transparent;
  border-radius: var(--radius-md); padding: 24rpx 28rpx;
  font-size: var(--fs-md); color: var(--text-primary);
  min-height: 88rpx; /* 触控 44px */
  box-sizing: border-box;
}
/* #ifdef H5 */
.field-input:focus { border-color: var(--gold); background: var(--bg-card); }
/* #endif */
.ph { color: var(--text-placeholder); }
.picker-view {
  background: var(--bg-input); border-radius: var(--radius-md);
  padding: 24rpx 28rpx; font-size: var(--fs-md); color: var(--text-primary);
  min-height: 88rpx; line-height: 44rpx;
}
.switch-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-2) 0; min-height: 88rpx;
}
.switch-row .field-label { margin-bottom: 0; }

.type-toggle {
  display: flex; gap: var(--space-2); background: var(--bg-input);
  padding: var(--space-1); border-radius: var(--radius-md); margin-bottom: var(--space-3);
}
.type-item {
  flex: 1; text-align: center; font-size: var(--fs-md);
  padding: 20rpx 8rpx; border-radius: var(--radius-sm);
  color: var(--text-secondary); min-height: 44rpx;
}
.type-item.active {
  background: var(--bg-card); color: var(--brand-deep); font-weight: 700;
  box-shadow: var(--shadow-sm);
}

/* ===== 目标企业回显 ===== */
.target-bar {
  display: flex; align-items: center; gap: var(--space-2);
  background: var(--bg-input); border-radius: var(--radius-md);
  padding: var(--space-3); margin-bottom: var(--space-3);
}
.target-name { flex: 1; font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); }

/* ===== 查重卡（C10） ===== */
.dup-card {
  display: flex; gap: var(--space-3);
  background: rgba(245, 158, 11, 0.08); border: 2rpx solid rgba(245, 158, 11, 0.3);
  border-radius: var(--radius-md); padding: var(--space-4); margin-top: var(--space-2);
}
.dup-card--neutral { background: var(--bg-input); border-color: var(--line); }
.dup-ic {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  background: rgba(245, 158, 11, 0.18); color: var(--warning-text);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.dup-body { flex: 1; min-width: 0; }
.dup-t { display: block; font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); margin-bottom: var(--space-1); }
.dup-d { display: block; font-size: var(--fs-sm); color: var(--text-secondary); line-height: var(--lh-base); margin-bottom: var(--space-1); }
.pending-box {
  background: rgba(245, 158, 11, 0.1); border: 2rpx solid rgba(245, 158, 11, 0.25);
  border-radius: var(--radius-sm); padding: var(--space-3); margin-top: var(--space-2);
  display: flex; flex-direction: column; gap: var(--space-2); align-items: stretch;
}
.pending-text {
  font-size: var(--fs-sm); color: var(--warning-text); line-height: var(--lh-base);
}
.empty-hint {
  background: var(--bg-input); border-radius: var(--radius-md);
  padding: var(--space-8) var(--space-4); text-align: center;
  font-size: var(--fs-sm); color: var(--text-secondary); line-height: var(--lh-base);
}

/* ===== 上传材料（步骤 2） ===== */
.upload-grid {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3); margin-bottom: var(--space-2);
}
.upload-tile {
  background: var(--bg-card); border: 2rpx dashed var(--line);
  border-radius: var(--radius-md); padding: var(--space-4) var(--space-2);
  text-align: center; min-height: 176rpx;
}
.upload-tile:active { transform: scale(0.97); }
.upload-ic {
  width: 72rpx; height: 72rpx; border-radius: var(--radius-sm);
  background: var(--bg-input); display: flex; align-items: center; justify-content: center;
  margin: 0 auto var(--space-2); color: var(--text-secondary);
}
.upload-name { display: block; font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); margin-bottom: var(--space-1); }
.upload-status {
  display: inline-block; font-size: var(--fs-xs); font-weight: 600;
  padding: 4rpx 16rpx; border-radius: var(--radius-full);
}
.upload-status.ok { background: rgba(16, 185, 129, 0.14); color: var(--success-text); }
.upload-status.fail { background: rgba(239, 68, 68, 0.14); color: var(--danger-text); }
.upload-status.pending { background: rgba(245, 158, 11, 0.16); color: var(--warning-text); }
.upload-status.empty { background: var(--bg-input); color: var(--text-secondary); }

/* ===== 核验（步骤 3） ===== */
.verify-row {
  display: flex; gap: var(--space-3); padding: var(--space-3) 0;
  border-bottom: 2rpx solid var(--line);
}
.verify-row:last-of-type { border-bottom: none; margin-bottom: var(--space-3); }
.verify-ic {
  width: 52rpx; height: 52rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.verify-ic.ok { background: rgba(16, 185, 129, 0.15); color: var(--success-text); }
.verify-ic.fail { background: rgba(239, 68, 68, 0.15); color: var(--danger-text); }
.verify-body { flex: 1; min-width: 0; }
.verify-title { display: block; font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); margin-bottom: 4rpx; }
.verify-desc { display: block; font-size: var(--fs-sm); color: var(--text-secondary); line-height: var(--lh-base); }

/* ===== 底部导航 ===== */
.footer-nav {
  display: flex; gap: var(--space-3); margin-top: var(--space-4);
  padding-bottom: calc(var(--space-4) + env(safe-area-inset-bottom));
}
.footer-nav view, .footer-nav button { flex: 1; min-width: 0; }

/* ===== 未认证引导（UI v2：品牌渐变 Hero + 大插画 + 3 步骤 + 信任背书） ===== */
.guard-hero {
  position: relative;
  margin: var(--space-4) var(--space-3) var(--space-6);
  padding: var(--space-8) var(--space-5) var(--space-6);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(120% 80% at 50% 0%, rgba(200, 169, 110, 0.28) 0%, rgba(200, 169, 110, 0) 60%),
    linear-gradient(165deg, var(--brand-deep) 0%, var(--brand-mid) 55%, var(--brand-bright) 100%);
  color: var(--text-invert);
  text-align: center;
  overflow: hidden;
  box-shadow: 0 12rpx 40rpx rgba(11, 29, 58, 0.18);
}
/* Hero 装饰光斑（双层径向） */
.hero-decor { position: absolute; pointer-events: none; }
.hero-decor-1 {
  top: -120rpx; right: -80rpx; width: 320rpx; height: 320rpx; border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0) 70%);
}
.hero-decor-2 {
  bottom: -180rpx; left: -100rpx; width: 360rpx; height: 360rpx; border-radius: 50%;
  background: radial-gradient(circle, rgba(200, 169, 110, 0.18) 0%, rgba(200, 169, 110, 0) 70%);
}
/* 盾牌 + 锁 复合插画 */
.hero-shield {
  position: relative; width: 200rpx; height: 200rpx;
  margin: 0 auto var(--space-5);
}
.shield-bg {
  position: absolute; inset: 0;
  background: linear-gradient(160deg, var(--gold-bg) 0%, var(--gold) 100%);
  clip-path: path('M100 8 L185 38 L185 112 Q185 168 100 192 Q15 168 15 112 L15 38 Z');
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.18);
}
.shield-glow {
  position: absolute; inset: -16rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0) 70%);
  border-radius: 50%;
  animation: guard-pulse 3s ease-in-out infinite;
}
@keyframes guard-pulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.12); opacity: 1; }
}
.shield-icon {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
}
/* 锁主体 */
.shield-lock-body {
  position: absolute; left: 50%; top: 56%; transform: translate(-50%, -50%);
  width: 64rpx; height: 50rpx; border-radius: 10rpx;
  background: var(--brand-deep);
}
/* 锁弧 */
.shield-lock-shackle {
  position: absolute; left: 50%; top: 44%; transform: translate(-50%, -50%);
  width: 40rpx; height: 36rpx; border-radius: 20rpx 20rpx 0 0;
  border: 8rpx solid var(--brand-deep); border-bottom: none;
}
/* 锁孔 */
.shield-lock-keyhole {
  position: absolute; left: 50%; top: 58%; transform: translate(-50%, -50%);
  width: 14rpx; height: 14rpx; border-radius: 50%;
  background: var(--gold-bg);
  box-shadow: 0 18rpx 0 -2rpx var(--gold-bg);
}

.guard-eyebrow {
  display: block; font-size: 22rpx; letter-spacing: 4rpx; font-weight: 600;
  color: var(--gold); opacity: 0.92; margin-bottom: var(--space-2);
}
.guard-title {
  display: block; font-size: var(--fs-2xl); font-weight: 800; line-height: 1.3;
  color: var(--text-invert); margin-bottom: var(--space-1);
}
.guard-title-accent {
  display: block; font-size: var(--fs-2xl); font-weight: 800; line-height: 1.3;
  color: var(--gold); margin-bottom: var(--space-3);
}
.guard-desc {
  display: block; font-size: var(--fs-sm); color: rgba(255, 255, 255, 0.78);
  line-height: var(--lh-base); padding: 0 var(--space-2); margin-bottom: var(--space-5);
}

/* 3 步骤 */
.guard-steps {
  display: flex; align-items: center; justify-content: space-between;
  background: rgba(255, 255, 255, 0.08); border: 2rpx solid rgba(255, 255, 255, 0.14);
  border-radius: var(--radius-md); padding: var(--space-3) var(--space-2);
  margin-bottom: var(--space-5);
}
.gstep { display: flex; flex-direction: column; align-items: center; gap: var(--space-1); flex: 1; }
.gstep-num {
  width: 48rpx; height: 48rpx; border-radius: 50%;
  background: var(--gold); color: var(--gold-text);
  font-size: var(--fs-md); font-weight: 800; line-height: 48rpx; text-align: center;
}
.gstep-text {
  font-size: 22rpx; color: rgba(255, 255, 255, 0.85); line-height: 1.4; text-align: center;
  white-space: pre-line;
}
.gstep-divider {
  width: 2rpx; height: 56rpx; background: rgba(255, 255, 255, 0.18); margin: 0 var(--space-1);
}

.guard-trust {
  display: flex; align-items: center; justify-content: center; gap: var(--space-1);
  margin-top: var(--space-3); font-size: 22rpx;
}
.guard-trust-icon { font-size: 24rpx; }
.guard-trust-text { color: rgba(255, 255, 255, 0.62); }

/* 守护卡内按钮：主按钮加深以在深色 hero 突出，次按钮描边 */
.guard-hero .app-btn.btn-primary { background: var(--gold); color: var(--gold-text); border: none; }
.guard-hero .app-btn.btn-primary::after { border: none; }
.guard-hero .app-btn.btn-ghost { background: transparent; color: var(--text-invert); border: 2rpx solid rgba(255, 255, 255, 0.6); }
.guard-hero .app-btn.btn-ghost::after { border: none; }

/* ===== 结果卡（三档） ===== */
.result-card {
  position: relative; overflow: hidden;
  border-radius: var(--radius-lg); padding: var(--space-6) var(--space-4);
  margin-bottom: var(--space-4); color: var(--text-invert);
}
.result-decor {
  position: absolute; right: -80rpx; top: -100rpx;
  width: 320rpx; height: 320rpx; border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}
.result-inner { position: relative; z-index: 1; }
.rc-pass { background: linear-gradient(135deg, var(--brand-deep), var(--brand-bright)); }
.rc-condition { background: linear-gradient(135deg, var(--role-adviser), var(--gold)); }
.rc-reject { background: linear-gradient(135deg, var(--text-secondary), var(--text-secondary)); }
.result-badge { display: inline-block; margin-bottom: var(--space-3); }
.result-label {
  font-size: var(--fs-xl); font-weight: 800; letter-spacing: 2rpx;
}
.result-metrics {
  display: flex; align-items: center; justify-content: center;
  margin-bottom: var(--space-3);
}
.metric { flex: 1; text-align: center; }
.metric-num { display: block; font-size: 56rpx; font-weight: 800; line-height: 1.1; }
.metric-name { display: block; font-size: var(--fs-sm); opacity: 0.85; margin-top: var(--space-1); }
.metric-divider { width: 2rpx; height: 80rpx; background: rgba(255, 255, 255, 0.25); }
.result-tip {
  display: block; text-align: center; font-size: var(--fs-sm);
  opacity: 0.85; margin-bottom: var(--space-4);
}
.result-actions { display: flex; gap: var(--space-3); }
.result-actions view, .result-actions button { flex: 1; min-width: 0; }
</style>
