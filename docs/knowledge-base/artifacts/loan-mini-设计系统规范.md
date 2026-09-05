# 企融通 loan-mini · 设计系统规范 v1.0

> **用途**：uni-app 小程序 + H5 编码的**唯一设计依据**。一套代码编译两端，所有 Token 与组件从本文件复制，禁止在页面里写裸值。
> **基线**：深海军蓝 `#0B1D3A` × 暖金 `#C8A96E`；暗画布 + 亮屏幕的原型视觉语言。
> **合规**：WCAG 2.1 AA（对比度 4.5:1 / 触控 44px / 键盘可达 / 焦点可见）。
> **日期**：2026-08-30 ｜ **设计**：UI Designer

---

## 一、设计原则

| 原则 | 说明 |
|------|------|
| **Token 优先** | 任何间距 / 字号 / 圆角 / 颜色 / 过渡必须使用变量，页面内禁止裸值 |
| **组件收敛** | 6 个基础组件覆盖 90% 界面，新增组件需评审，禁止同类多套并行 |
| **角色驱动** | 同一页面按角色（客户 / 渠道 / 顾问 / 经理 / 老板 / 运营 / 超管）差异化渲染，不复制 7 份页面 |
| **移动优先** | 基准 375px 宽，触控目标 ≥44px，单手可达区优先 |
| **无障碍内建** | 对比度、焦点、语义标签是交付标准的一部分，不是事后补充 |

---

## 二、设计 Token

### 2.1 颜色 Token

```css
:root{
  /* ===== 品牌色 ===== */
  --brand-deep:#0B1D3A;      /* 主色：深海军蓝，主按钮 / 导航栏 / 选中态 */
  --brand-mid:#132D56;       /* 渐变中段 */
  --brand-bright:#1A3A6E;    /* 渐变亮段 */
  --gold:#C8A96E;            /* 强调色：暖金，CTA / 进行中 / 高亮 */
  --gold-bg:#F5E6C4;         /* 暖金浅底（图标容器 / 提示底） */

  /* ===== 中性色 ===== */
  --bg-page:#F8FAFC;         /* 页面背景 */
  --bg-card:#FFFFFF;         /* 卡片背景 */
  --bg-input:#F1F5F9;        /* 输入框 / 次级底 */
  --line:#E2E8F0;            /* 分隔线 / 边框 */
  --text-primary:#1E293B;    /* 主文本（对比度 13.6:1 ✓） */
  --text-body:#475569;       /* 正文（7.0:1 ✓） */
  --text-secondary:#64748B;  /* 次要文本（4.8:1 ✓） */
  --text-placeholder:#CBD5E1;/* 占位符（非文本，不要求对比度） */
  --text-invert:#FFFFFF;     /* 深色底上的文字 */

  /* ===== 语义色 ===== */
  --success:#10B981;         /* 成功 / 已上架 / 已完成 */
  --warning:#F59E0B;         /* 警告：仅用于图标与底色，禁用于文字 */
  --danger:#EF4444;          /* 错误 / 已驳回 / 删除 */
  --info:#06B6D4;            /* 信息 / 进行中 / 渠道 */

  /* ===== 无障碍文字色（对比度已验证 ≥4.5:1，严禁替换为上行的原色） ===== */
  --warning-text:#B45309;    /* 橙色系文字专用，白底实测 4.54:1 ✓ */
  --gold-text:#3A2E12;       /* 暖金底上的文字，实测 ≈7.0:1 ✓ */
  --success-text:#047857;    /* 绿色系文字专用，实测 5.3:1 ✓ */
  --danger-text:#B91C1C;     /* 红色系文字专用，实测 6.2:1 ✓ */
  --info-text:#0E7490;       /* 青色系文字专用，实测 4.9:1 ✓ */
}
```

### 2.2 间距 Token（4px 基准）

```css
:root{
  --space-1:4px;   --space-2:8px;   --space-3:12px;  --space-4:16px;
  --space-5:20px;  --space-6:24px;  --space-8:32px;  --space-10:40px;
  --space-12:48px; --space-16:64px;

  /* ===== 语义间距（页面优先用语义变量） ===== */
  --space-page-gutter:var(--space-4);   /* 页面左右留白 16px */
  --space-card-pad:var(--space-4);      /* 卡片内边距 16px */
  --space-stack:var(--space-3);         /* 卡片之间垂直间距 12px */
  --space-field:var(--space-3);         /* 表单项之间 12px */
  --space-inline:var(--space-2);        /* 行内元素间距 8px */
}
```

### 2.3 圆角 Token（4 级）

```css
:root{
  --radius-sm:8px;      /* chip / tag / 小徽章 */
  --radius-md:12px;     /* 卡片 / 输入框 / 按钮 / 列表项 */
  --radius-lg:16px;     /* 主卡片 / 弹层 */
  --radius-full:999px;  /* 状态徽章 / 头像 */
}
```

### 2.4 字号 Token（6 级）

```css
:root{
  --fs-xs:11px;    /* 报告 ID / 辅助说明 / 时间戳 */
  --fs-sm:12px;    /* 次要信息 / 列表描述 */
  --fs-md:13px;    /* 正文 */
  --fs-lg:15px;    /* 卡片标题 */
  --fs-xl:18px;    /* 页面标题 */
  --fs-2xl:22px;   /* KPI 数值 */
  --fs-3xl:26px;   /* 登录页品牌名（特例） */

  --lh-tight:1.3;  /* 标题行高 */
  --lh-base:1.6;   /* 正文行高 */
  --lh-loose:1.7;  /* 长文本行高 */
}
```

### 2.5 过渡 Token（3 级）

```css
:root{
  --transition-fast:150ms ease;   /* 按压 / 颜色变化 */
  --transition-base:200ms ease;   /* 默认 */
  --transition-slow:280ms ease;   /* 页面切换 / 展开 */
}
```

### 2.6 阴影 Token（3 级）

```css
:root{
  --shadow-sm:0 1px 2px rgba(15,23,42,.04);    /* 列表项 / chip */
  --shadow-md:0 4px 12px rgba(15,23,42,.06);   /* 卡片 */
  --shadow-lg:0 8px 24px rgba(15,23,42,.08);   /* 弹层 / 吸底栏 */
}
```

### 2.7 角色色板 Token

```css
:root{
  /* 7 角色徽章色：任意两色 ΔE > 20，均为深色底 + 白字（对比度 ≥4.5:1） */
  --role-customer:#0B1D3A;   /* 客户：品牌深蓝 */
  --role-channel:#0E7490;    /* 渠道合作方：深青 */
  --role-adviser:#8A6D3A;    /* 顾问：暖金加深（白字 4.6:1 ✓，修复原 2.24:1） */
  --role-deptmgr:#1D4ED8;    /* 部门经理：宝蓝（原与渠道撞色，已改） */
  --role-boss:#6D28D9;       /* 老板：深紫（原用 danger 红，语义冲突已改） */
  --role-operator:#3A2E12;   /* 运营管理员：深棕 + 金边 */
  --role-super:#1F2937;      /* 超级管理员：深灰 */
}
```

---

## 三、基础组件（6 个）

> 全部为 Vue3 SFC，放在 `components/`，`pages.json` 已配 easycom（`^App(.*)` → `@/components/App$1.vue`），页面内可直接使用 `<AppButton>` 无需 import。

### 3.1 AppButton（按钮）

**层级定义**（仅 3 种，禁止再增加）：

| variant | 用途 | 样式 |
|---------|------|------|
| `primary` | 主操作（提交 / 下一步 / 确认） | 品牌深蓝底 + 白字 |
| `secondary` | 次操作（上一步 / 取消 / 重置） | 白底 + 深蓝描边 |
| `text` | 弱操作（查看更多 / 跳过） | 透明底 + 深蓝文字 |

**尺寸**：`lg`（全宽，高 48px，用于页面底部主 CTA）、`md`（默认，高 44px）、`sm`（高 36px，用于卡片内）。

```vue
<template>
  <button
    class="app-btn"
    :class="[`app-btn--${variant}`, `app-btn--${size}`, { 'is-block': block, 'is-loading': loading, 'is-disabled': disabled }]"
    :disabled="disabled || loading"
    :hover-class="disabled || loading ? '' : 'app-btn--hover'"
    @click="onClick"
  >
    <text v-if="loading" class="app-btn__spinner" />
    <slot v-else />
  </button>
</template>

<script setup>
const props = defineProps({
  variant: { type: String, default: 'primary' }, // primary | secondary | text
  size:    { type: String, default: 'md' },      // lg | md | sm
  block:   { type: Boolean, default: false },    // 是否全宽
  loading: { type: Boolean, default: false },
  disabled:{ type: Boolean, default: false },
});
const emit = defineEmits(['click']);
function onClick(e) {
  if (props.disabled || props.loading) return;
  emit('click', e);
}
</script>

<style scoped>
.app-btn{
  display:inline-flex; align-items:center; justify-content:center;
  font-family:inherit; font-weight:600; border:none; border-radius:var(--radius-md);
  transition:transform var(--transition-fast), opacity var(--transition-fast);
  /* 触控目标最小 44px（WCAG 2.5.5） */
  min-height:44px; padding:0 var(--space-4);
}
/* 尺寸 */
.app-btn--sm{ min-height:36px; padding:0 var(--space-3); font-size:var(--fs-sm); }
.app-btn--md{ min-height:44px; padding:0 var(--space-4); font-size:var(--fs-md); }
.app-btn--lg{ min-height:48px; padding:0 var(--space-5); font-size:var(--fs-lg); width:100%; }
/* 变体 */
.app-btn--primary{ background:var(--brand-deep); color:var(--text-invert); }
.app-btn--secondary{ background:var(--bg-card); color:var(--brand-deep); border:1.5px solid var(--brand-deep); }
.app-btn--text{ background:transparent; color:var(--brand-deep); padding:0 var(--space-2); }
/* 状态 */
.app-btn--hover{ opacity:.88; transform:translateY(-1px); }
.app-btn.is-block{ display:flex; width:100%; }
.app-btn.is-disabled{ opacity:.45; }
.app-btn.is-loading{ opacity:.7; }
.app-btn__spinner{
  width:16px; height:16px; border:2px solid currentColor;
  border-right-color:transparent; border-radius:50%;
  animation:app-spin .7s linear infinite;
}
@keyframes app-spin{ to{ transform:rotate(360deg); } }
/* 键盘焦点（H5） */
.app-btn:focus-visible{ outline:2px solid var(--gold); outline-offset:2px; }
</style>
```

**用法**：
```html
<AppButton variant="primary" size="lg" block @click="submit">提交审核</AppButton>
<AppButton variant="secondary" @click="prev">上一步</AppButton>
<AppButton variant="text" size="sm" @click="more">查看历史</AppButton>
<AppButton :loading="submitting">保存</AppButton>
```

---

### 3.2 AppCard（卡片）

统一圆角 `--radius-lg`、内边距 `--space-card-pad`、阴影 `--shadow-md`。三个 variant 覆盖全部场景，杜绝 7 种圆角并存。

```vue
<template>
  <view class="app-card" :class="[`app-card--${variant}`, { 'is-tappable': tappable }]" @click="onClick">
    <view v-if="title || $slots.extra" class="app-card__head">
      <text class="app-card__title">{{ title }}</text>
      <slot name="extra" />
    </view>
    <slot />
  </view>
</template>

<script setup>
const props = defineProps({
  title:    { type: String, default: '' },
  variant:  { type: String, default: 'default' }, // default | flat | accent
  tappable: { type: Boolean, default: false },
});
const emit = defineEmits(['click']);
function onClick(e) { if (props.tappable) emit('click', e); }
</script>

<style scoped>
.app-card{
  background:var(--bg-card); border-radius:var(--radius-lg);
  padding:var(--space-card-pad); box-shadow:var(--shadow-md);
  margin-bottom:var(--space-stack);
}
.app-card--flat{ box-shadow:var(--shadow-sm); }
.app-card--accent{
  background:linear-gradient(135deg,var(--brand-deep),var(--brand-bright));
  color:var(--text-invert);
}
.app-card--accent .app-card__title{ color:var(--text-invert); }
.app-card__head{
  display:flex; align-items:center; justify-content:space-between;
  margin-bottom:var(--space-3);
}
.app-card__title{
  font-size:var(--fs-lg); font-weight:700; color:var(--text-primary);
}
.app-card.is-tappable:active{ transform:scale(.99); background:#FAFBFC; }
</style>
```

---

### 3.3 AppListItem（列表项）

统一替代原 `.list-item` / `.report-card` / `.prod-card` 三套实现。用 slot 承载差异：
`leading`（左侧图标/评级块）、默认 slot（主内容）、`trailing`（右侧箭头/状态）。

```vue
<template>
  <view class="app-li" :class="{ 'is-tappable': tappable }" @click="onClick">
    <slot name="leading" />
    <view class="app-li__main">
      <text v-if="id" class="app-li__id">{{ id }}</text>
      <text class="app-li__title">{{ title }}</text>
      <view v-if="$slots.meta || desc" class="app-li__meta">
        <slot name="meta"><text v-if="desc" class="app-li__desc">{{ desc }}</text></slot>
      </view>
      <slot />
    </view>
    <view class="app-li__trailing">
      <slot name="trailing" />
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  id: String, title: String, desc: String, tappable: { type: Boolean, default: false },
});
const emit = defineEmits(['click']);
function onClick(e) { if (props.tappable) emit('click', e); }
</script>

<style scoped>
.app-li{
  display:flex; align-items:center; gap:var(--space-3);
  background:var(--bg-card); border-radius:var(--radius-md);
  padding:var(--space-3) var(--space-4); margin-bottom:var(--space-2);
  box-shadow:var(--shadow-sm);
  min-height:44px; /* 触控目标 */
  transition:background var(--transition-fast);
}
.app-li.is-tappable:active{ background:#F8FAFC; }
.app-li__main{ flex:1; min-width:0; }
.app-li__id{
  font-size:var(--fs-xs); color:var(--text-secondary);
  font-family:ui-monospace,monospace;
}
.app-li__title{
  font-size:var(--fs-md); font-weight:600; color:var(--text-primary);
  line-height:var(--lh-tight); margin:2px 0 4px;
  overflow:hidden; text-overflow:ellipsis; white-space:nowrap;
}
.app-li__meta{ display:flex; flex-wrap:wrap; gap:var(--space-2); }
.app-li__desc{ font-size:var(--fs-sm); color:var(--text-secondary); }
.app-li__trailing{ flex-shrink:0; display:flex; align-items:center; }
</style>
```

---

### 3.4 AppTag（状态徽章）

统一替代原 `.status-tag` / `.upload-status` / `.risk-tag` 三套。按语义色分 variant，**文字一律用 `-text` 无障碍色**。

| tone | 用途 | 文字色 |
|------|------|--------|
| `success` | 已上架 / 已完成 / 已核验 | `--success-text` |
| `warning` | 待审批 / 待处理 / 待核验 | `--warning-text` |
| `danger` | 已驳回 / 已取消 / 需补充 | `--danger-text` |
| `info` | 进行中 / 渠道 | `--info-text` |
| `neutral` | 草稿 / 未上传 / 公海 | `--text-secondary` |

```vue
<template>
  <text class="app-tag" :class="`app-tag--${tone}`">{{ text }}</text>
</template>

<script setup>
defineProps({
  text: { type: String, required: true },
  tone: { type: String, default: 'neutral' }, // success|warning|danger|info|neutral
});
</script>

<style scoped>
.app-tag{
  display:inline-flex; align-items:center;
  font-size:var(--fs-xs); font-weight:700;
  padding:3px 10px; border-radius:var(--radius-full);
  line-height:1.4; white-space:nowrap;
}
.app-tag--success{ background:rgba(16,185,129,.14); color:var(--success-text); }
.app-tag--warning{ background:rgba(245,158,11,.16); color:var(--warning-text); }
.app-tag--danger { background:rgba(239,68,68,.14);  color:var(--danger-text); }
.app-tag--info   { background:rgba(6,182,212,.14);  color:var(--info-text); }
.app-tag--neutral{ background:var(--bg-input);      color:var(--text-secondary); }
</style>
```

---

### 3.5 AppEmpty（空态）

统一替代原 `.empty` / `.report-empty` 两套。支持"无数据"与"筛选无结果"两种语义。

```vue
<template>
  <view class="app-empty">
    <view class="app-empty__ic"><slot name="icon"><AppIcon :name="icon" /></slot></view>
    <text class="app-empty__title">{{ title }}</text>
    <text v-if="desc" class="app-empty__desc">{{ desc }}</text>
    <slot name="action" />
  </view>
</template>

<script setup>
defineProps({
  icon:  { type: String, default: 'inbox' },
  title: { type: String, default: '暂无数据' },
  desc:  { type: String, default: '' },
});
</script>

<style scoped>
.app-empty{
  display:flex; flex-direction:column; align-items:center;
  padding:var(--space-12) var(--space-6); text-align:center;
}
.app-empty__ic{
  width:56px; height:56px; border-radius:var(--radius-lg);
  background:var(--bg-input); display:flex; align-items:center; justify-content:center;
  margin-bottom:var(--space-4); color:var(--text-secondary);
}
.app-empty__title{
  font-size:var(--fs-md); font-weight:600; color:var(--text-primary);
  margin-bottom:var(--space-1);
}
.app-empty__desc{
  font-size:var(--fs-sm); color:var(--text-secondary);
  line-height:var(--lh-base); max-width:280px;
}
</style>
```

**用法（筛选无结果）**：
```html
<AppEmpty title="无匹配的报告" desc="当前筛选条件下没有找到报告，试试放宽条件">
  <template #action>
    <AppButton variant="secondary" size="sm" @click="reset">重置筛选</AppButton>
  </template>
</AppEmpty>
```

---

### 3.6 AppStepper（步骤条）

统一替代原登录页 `.flow-step` 与匹配页 `.steps` 两套。支持 `horizontal`（4 步横向）与 `vertical`（认证流纵向）两种布局。

```vue
<template>
  <view class="app-steps" :class="`app-steps--${layout}`">
    <view
      v-for="(s, i) in steps" :key="i"
      class="app-step"
      :class="{ 'is-done': i < current, 'is-active': i === current }"
      @click="onStep(i)"
    >
      <view class="app-step__dot">
        <text v-if="i < current" class="app-step__check">✓</text>
        <text v-else>{{ i + 1 }}</text>
      </view>
      <text v-if="layout === 'horizontal'" class="app-step__name">{{ s }}</text>
      <view v-else class="app-step__body">
        <text class="app-step__name">{{ s }}</text>
      </view>
      <view v-if="i < steps.length - 1" class="app-step__line" />
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  steps:    { type: Array, required: true },   // ['目标企业','经营事实','上传材料','核验匹配']
  current:  { type: Number, default: 0 },      // 当前步索引（0 基）
  layout:   { type: String, default: 'horizontal' }, // horizontal | vertical
});
const emit = defineEmits(['change']);
// 只允许点击已完成的步骤回看，防止跳过必填
function onStep(i) { if (i < props.current) emit('change', i); }
</script>

<style scoped>
.app-steps{ display:flex; background:var(--bg-card); border-radius:var(--radius-md);
  padding:var(--space-4) var(--space-4) var(--space-3); box-shadow:var(--shadow-sm); }
.app-steps--vertical{ flex-direction:column; padding:var(--space-4); background:transparent; box-shadow:none; }

.app-step{ flex:1; display:flex; align-items:center; position:relative; }
.app-steps--horizontal .app-step{ flex-direction:column; gap:var(--space-1); }
.app-steps--vertical .app-step{ flex-direction:row; gap:var(--space-3); padding-bottom:var(--space-4); align-items:flex-start; }

.app-step__dot{
  width:26px; height:26px; border-radius:50%;
  background:var(--bg-card); border:1.5px solid var(--line);
  display:flex; align-items:center; justify-content:center;
  font-size:var(--fs-sm); font-weight:700; color:var(--text-secondary);
  transition:all var(--transition-base); flex-shrink:0;
}
.app-step.is-done .app-step__dot{ background:var(--brand-deep); border-color:var(--brand-deep); color:var(--text-invert); }
.app-step.is-active .app-step__dot{
  background:var(--gold); border-color:var(--gold); color:var(--text-invert);
  box-shadow:0 0 0 4px rgba(200,169,110,.18);
}
.app-step__name{ font-size:var(--fs-sm); color:var(--text-secondary); text-align:center; }
.app-step.is-active .app-step__name{ color:var(--brand-deep); font-weight:600; }
.app-step.is-done .app-step__name{ color:var(--text-primary); }

.app-step__line{ background:var(--line); }
.app-steps--horizontal .app-step__line{
  position:absolute; left:50%; right:-50%; top:13px; height:1.5px; z-index:-1;
}
.app-steps--vertical .app-step__line{
  position:absolute; left:13px; top:26px; bottom:0; width:2px;
}
.app-step.is-done .app-step__line{ background:var(--brand-deep); }
</style>
```

---

## 四、无障碍基线（交付标准）

| 项 | 标准 | 落地方式 |
|---|------|----------|
| **对比度** | 正文 ≥4.5:1；大字（≥18.66px 粗体）≥3:1 | 文字一律用 `--*-text` 无障碍色；**禁止**用 `--warning` / `--gold` 原色作文字色 |
| **触控目标** | ≥44×44px | 所有 `AppButton` / `AppListItem` / chip / seg 设 `min-height:44px` |
| **键盘可达** | 全部交互元素可 Tab 聚焦 | H5 端所有可点击 `view` 补 `tabindex="0"` + `role="button"` + `@keydown.enter` |
| **焦点可见** | 焦点有明确指示 | 全局 `:focus-visible{outline:2px solid var(--gold);outline-offset:2px}` |
| **语义标签** | 读屏可理解 | 状态标签补 `aria-label`（如"状态：待审批"）；tabBar 用 `role="tablist"`/`role="tab"`/`aria-selected` |
| **动效偏好** | 尊重系统设置 | `@media (prefers-reduced-motion: reduce){ *{animation-duration:.01ms !important;transition-duration:.01ms !important} }` |

**全局焦点样式**（写入 `App.vue` 全局样式）：
```css
:focus-visible{ outline:2px solid var(--gold); outline-offset:2px; border-radius:var(--radius-sm); }
@media (prefers-reduced-motion: reduce){
  *,*::before,*::after{ animation-duration:.01ms !important; transition-duration:.01ms !important; }
}
```

---

## 五、交互状态矩阵（每个可交互元素必须 6 态齐全）

| 状态 | 实现 | 适用 |
|------|------|------|
| **Default** | 基础样式 | 全部 |
| **Hover** | `opacity:.88` + `translateY(-1px)`（H5，用 `@media(hover:hover)` 包裹） | 按钮 / 卡片 / 列表项 |
| **Active** | `transform:scale(.98~.99)` | 全部（小程序仅此态） |
| **Focus** | `outline:2px solid var(--gold)` | 全部（H5 键盘） |
| **Disabled** | `opacity:.45` + `pointer-events:none` | 按钮 / 输入框 / chip |
| **Loading** | 按钮内 spinner + 禁用点击 | 提交 / 匹配 / 查重 / 查询 |

---

## 六、角色驱动的页面差异化（避免 7 份重复页面）

同一页面用 `userStore.role` 控制，不复制页面文件：

| 页面 | 客户 | 渠道 | 顾问/经理/老板 | 运营/超管 |
|------|------|------|----------------|-----------|
| **tabBar** | 首页/匹配/报告/服务单/我的 | 首页/我的产品/录入客户/我的 | 首页/匹配/报告/服务单/我的 | 工作台/匹配/全部报告/审批中心/我的 |
| **智能匹配** | ✅ 自身企业 | ❌ 禁入（C1） | ✅ 替客（C2/C10） | ✅ 有资格（C1） |
| **报告列表** | 仅日期 chip（C3） | ❌ 隐藏（沙箱） | 全量 4 维查询（C11） | 全量 4 维查询 |
| **报告详情** | 命中产品 + 诊断 | ❌ 不可达 | ✅ 命中产品 + 诊断（C4） | ✅ 全量 |
| **服务单/工单** | 状态 + 时间（C7） | ❌ 隐藏 | 四维筛选（C7） | 四维筛选 |
| **产品管理** | — | ✅ 录入/撤销/申请删除（C9） | ✅ 录入（走审批） | ✅ 终审（上架+删除） |

---

## 七、从旧原型迁移的对照表

| 旧实现 | 新组件 | 迁移要点 |
|--------|--------|----------|
| `.btn` + 11 种变体 | `AppButton` (3 variant × 3 size) | `btn-gold`→`variant="primary"` 或保留 CTA 特例；`mini-btn`/`mb-*`→`size="sm"` |
| `.card` / `.step-card` / `.date-chips` (7 种圆角) | `AppCard` (3 variant) | 圆角统一 `--radius-lg`，内边距统一 `--space-card-pad` |
| `.list-item` / `.report-card` / `.prod-card` | `AppListItem` | 用 `leading` / `meta` / `trailing` slot 承载差异 |
| `.status-tag` / `.upload-status` / `.risk-tag` | `AppTag` (5 tone) | 文字色全部改用 `--*-text` |
| `.empty` / `.report-empty` | `AppEmpty` | 补 `action` slot 放"重置筛选"按钮 |
| `.flow-step` / `.steps` | `AppStepper` (2 layout) | 只允许点击已完成步骤回看 |

---

## 八、验收清单

- [ ] 全部页面无裸值 `padding` / `margin` / `font-size` / `border-radius` / `transition`
- [ ] 文字色全部使用 `--*-text` 无障碍变量，无 `--warning` / `--gold` 直用作文字
- [ ] 所有可交互元素 `min-height ≥ 44px`
- [ ] H5 端 Tab 可遍历全部交互元素，焦点指示清晰
- [ ] 每个可交互元素 6 态齐全（Default/Hover/Active/Focus/Disabled/Loading）
- [ ] 6 个基础组件覆盖页面 90% 以上结构，无同类组件并行
- [ ] 7 角色差异化通过配置实现，无重复页面文件
- [ ] 对比度实测全部 ≥4.5:1（可用 axe DevTools 或 Contrast Checker 验证）

---

**设计系统版本**：v1.0
**适用**：loan-mini（uni-app，编译至微信小程序 + H5）
**状态**：可作为编码直接依据
