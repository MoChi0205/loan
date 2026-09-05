<template>
  <view class="app-icon" :class="[`icon-${size}`]" :style="{ color }">
    <!-- 指南针：智能匹配（替代原靶心图标，小尺寸下形似喇叭令人迷惑） -->
    <view v-if="name === 'match'" class="ico ico-compass">
      <view class="compass-ring" />
      <view class="compass-needle" />
      <view class="compass-hub" />
    </view>

    <!-- 柱状图：报告 -->
    <view v-if="name === 'chart'" class="ico ico-chart">
      <view class="bar bar-1" />
      <view class="bar bar-2" />
      <view class="bar bar-3" />
    </view>

    <!-- 闪电：加速/认证 -->
    <view v-if="name === 'bolt'" class="ico ico-bolt">
      <view class="bolt-top" />
      <view class="bolt-bottom" />
    </view>

    <!-- 锁：未认证 -->
    <view v-if="name === 'lock'" class="ico ico-lock">
      <view class="lock-arc" />
      <view class="lock-body" />
    </view>

    <!-- 列表 -->
    <view v-if="name === 'list'" class="ico ico-list">
      <view class="li" />
      <view class="li" />
      <view class="li li-short" />
    </view>

    <!-- 对勾：已认证/成功 -->
    <view v-if="name === 'check'" class="ico ico-check">
      <view class="check-short" />
      <view class="check-long" />
    </view>

    <!-- 警告/提示 -->
    <view v-if="name === 'alert'" class="ico ico-alert">
      <view class="alert-bar" />
      <view class="alert-dot" />
    </view>

    <!-- 箭头右：更多 -->
    <view v-if="name === 'arrow'" class="ico ico-arrow">
      <view class="arrow-line" />
      <view class="arrow-head" />
    </view>

    <!-- 微信：双气泡（替代无字体文件的 iconfont 字符 &#xe900;） -->
    <view v-if="name === 'wechat'" class="ico ico-wechat">
      <view class="bubble-big">
        <view class="eye eye-l" />
        <view class="eye eye-r" />
      </view>
      <view class="bubble-small">
        <view class="eye eye-l" />
        <view class="eye eye-r" />
      </view>
    </view>

    <!-- 首页：小房子 -->
    <view v-if="name === 'home'" class="ico ico-home">
      <view class="home-roof" />
      <view class="home-body">
        <view class="home-door" />
      </view>
    </view>

    <!-- 我的：人形 -->
    <view v-if="name === 'mine'" class="ico ico-mine">
      <view class="mine-head" />
      <view class="mine-body" />
    </view>

    <!-- 银行/产品：柱廊 -->
    <view v-if="name === 'bank'" class="ico ico-bank">
      <view class="bank-beam" />
      <view class="bank-cols">
        <view class="bank-col" />
        <view class="bank-col" />
        <view class="bank-col" />
      </view>
    </view>

    <!-- 服务单：单据 -->
    <view v-if="name === 'order'" class="ico ico-order">
      <view class="order-frame">
        <view class="order-line" />
        <view class="order-line" />
        <view class="order-line order-line-short" />
      </view>
    </view>

    <!-- 刷新：环形箭头 -->
    <view v-if="name === 'refresh'" class="ico ico-refresh">
      <view class="refresh-arc" />
      <view class="refresh-head" />
    </view>

    <!-- 工作台：田字格 -->
    <view v-if="name === 'workbench'" class="ico ico-workbench">
      <view class="wb-cell" />
      <view class="wb-cell" />
      <view class="wb-cell" />
      <view class="wb-cell" />
    </view>

    <!-- 用户/线索：双人 -->
    <view v-if="name === 'users'" class="ico ico-users">
      <view class="user user-front">
        <view class="user-head" />
        <view class="user-body" />
      </view>
      <view class="user user-back">
        <view class="user-head" />
        <view class="user-body" />
      </view>
    </view>

    <!-- 企业：办公楼 -->
    <view v-if="name === 'enterprise'" class="ico ico-enterprise">
      <view class="enterprise-building">
        <view class="enterprise-window" v-for="i in 6" :key="i" />
      </view>
    </view>

    <!-- 个人：单人轮廓 -->
    <view v-if="name === 'person'" class="ico ico-person">
      <view class="person-head" />
      <view class="person-body" />
    </view>

    <!-- 趋势：经营数据（折线） -->
    <view v-if="name === 'trend'" class="ico ico-trend">
      <view class="trend-axis" />
      <view class="trend-line" />
      <view class="trend-dot trend-dot-1" />
      <view class="trend-dot trend-dot-2" />
      <view class="trend-dot trend-dot-3" />
    </view>

    <!-- 文档：企业年报（纸张+行） -->
    <view v-if="name === 'doc'" class="ico ico-doc">
      <view class="doc-sheet" />
      <view class="doc-line doc-line-1" />
      <view class="doc-line doc-line-2" />
    </view>

    <!-- 文件：附件（折叠角） -->
    <view v-if="name === 'file'" class="ico ico-file">
      <view class="file-body" />
      <view class="file-fold" />
    </view>

    <!-- 照片：照片（相框+山+太阳） -->
    <view v-if="name === 'photo'" class="ico ico-photo">
      <view class="photo-frame">
        <view class="photo-sun" />
        <view class="photo-mountain" />
      </view>
    </view>

    <!-- 顾问服务：耳麦 -->
    <view v-if="name === 'support'" class="ico ico-support">
      <view class="support-band" />
      <view class="support-ear support-ear-left" />
      <view class="support-ear support-ear-right" />
      <view class="support-mic" />
    </view>

    <!-- 分享：三点连线 -->
    <view v-if="name === 'share'" class="ico ico-share">
      <view class="share-line share-line-top" />
      <view class="share-line share-line-bottom" />
      <view class="share-node share-node-left" />
      <view class="share-node share-node-top" />
      <view class="share-node share-node-bottom" />
    </view>

    <!-- 搜索：放大镜 -->
    <view v-if="name === 'search'" class="ico ico-search">
      <view class="search-ring" />
      <view class="search-handle" />
    </view>

    <!-- 兜底：圆点 -->
    <view v-if="name !== 'match' && name !== 'chart' && name !== 'bolt' && name !== 'lock'
              && name !== 'list' && name !== 'check' && name !== 'alert' && name !== 'arrow'
              && name !== 'wechat' && name !== 'home' && name !== 'mine' && name !== 'bank'
              && name !== 'order' && name !== 'refresh' && name !== 'workbench' && name !== 'users'
              && name !== 'trend' && name !== 'doc' && name !== 'file' && name !== 'photo'
              && name !== 'support' && name !== 'share' && name !== 'enterprise' && name !== 'person'
              && name !== 'search'"
         class="ico ico-default" />
  </view>
</template>

<script setup>
/**
 * 图标组件（纯 view/CSS 绘制，零依赖）。
 *
 * <p>替代 emoji（🎯📊⚡🔒📋）——emoji 在 iOS/Android 渲染差异大、偏儿童感，
 * 与瑞幸风单色专业感冲突。CSS 绘制保证跨平台一致、颜色可继承主色。
 *
 * 可用 name：match(靶心) / chart(柱状) / bolt(闪电) / lock(锁) / list(列表)
 *           check(对勾) / alert(警告) / arrow(箭头) / wechat(微信双气泡)
 *           home(房子) / mine(人形) / bank(柱廊·银行/产品) / order(单据·服务单)
 *           refresh(环形箭头) / workbench(田字格) / users(双人·线索)
 *           support(顾问耳麦) / share(分享) / enterprise(企业) / person(个人)
 *
 * 用法：
 *   <AppIcon name="match" size="lg" color="#C8A96E" />
 */
defineProps({
  name: { type: String, default: 'dot' },
  /** 尺寸：sm(32rpx) / md(48rpx) / lg(64rpx) */
  size: { type: String, default: 'md' },
  /** 颜色，默认继承父级 currentColor；不传则用主色 */
  color: { type: String, default: '' },
});
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon-sm { width: 32rpx; height: 32rpx; }
.icon-md { width: 48rpx; height: 48rpx; }
.icon-lg { width: 64rpx; height: 64rpx; }

.ico {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ====== 指南针 compass（智能匹配） ====== */
.ico-compass {
  display: block;
}
.compass-ring {
  position: absolute;
  width: 92%;
  height: 92%;
  border: 4rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
  top: 4%;
  left: 4%;
}
.compass-needle {
  position: absolute;
  width: 16%;
  height: 50%;
  left: 42%;
  top: 25%;
  background: currentColor;
  clip-path: polygon(50% 0%, 100% 100%, 50% 78%, 0% 100%);
  opacity: 0.9;
}
.compass-hub {
  position: absolute;
  width: 18%;
  height: 18%;
  left: 41%;
  top: 41%;
  border-radius: 50%;
  background: var(--bg-card, #fff);
  border: 3rpx solid currentColor;
  box-sizing: border-box;
}

/* ====== 柱状图 chart ====== */
.ico-chart {
  align-items: flex-end;
  gap: 6rpx;
}
.bar {
  width: 18%;
  background: currentColor;
  border-radius: 4rpx 4rpx 0 0;
}
.bar-1 { height: 45%; opacity: 0.55; }
.bar-2 { height: 75%; opacity: 0.78; }
.bar-3 { height: 100%; }

/* ====== 闪电 bolt ====== */
.ico-bolt {
  flex-direction: column;
}
.bolt-top {
  width: 0;
  height: 0;
  border-left: 14rpx solid transparent;
  border-right: 14rpx solid transparent;
  border-bottom: 20rpx solid currentColor;
  transform: rotate(12deg);
}
.bolt-bottom {
  width: 0;
  height: 0;
  border-left: 14rpx solid transparent;
  border-right: 14rpx solid transparent;
  border-top: 20rpx solid currentColor;
  transform: rotate(-12deg);
  margin-top: -4rpx;
}

/* ====== 锁 lock ====== */
.ico-lock {
  flex-direction: column;
  justify-content: flex-end;
  padding-bottom: 4rpx;
}
.lock-arc {
  width: 46%;
  height: 26%;
  border: 5rpx solid currentColor;
  border-bottom: none;
  border-radius: 999rpx 999rpx 0 0;
}
.lock-body {
  width: 70%;
  height: 42%;
  background: currentColor;
  border-radius: 6rpx;
  margin-top: -2rpx;
}

/* ====== 列表 list ====== */
.ico-list {
  flex-direction: column;
  gap: 8rpx;
  align-items: flex-start;
}
.li {
  width: 100%;
  height: 5rpx;
  background: currentColor;
  border-radius: 3rpx;
  opacity: 0.85;
}
.li-short { width: 62%; opacity: 0.5; }

/* ====== 对勾 check ====== */
.ico-check {
  /* 用两条旋转的线段拼出对勾 */
  display: block;
  align-items: center;
  justify-content: center;
}
.check-short {
  position: absolute;
  width: 22%;
  height: 5rpx;
  background: currentColor;
  border-radius: 3rpx;
  transform: rotate(45deg);
  left: 22%;
  top: 54%;
}
.check-long {
  position: absolute;
  width: 46%;
  height: 5rpx;
  background: currentColor;
  border-radius: 3rpx;
  transform: rotate(-45deg);
  left: 36%;
  top: 44%;
}

/* ====== 警告 alert ====== */
.ico-alert {
  flex-direction: column;
  gap: 6rpx;
}
.alert-bar {
  width: 12%;
  height: 46%;
  background: currentColor;
  border-radius: 3rpx;
}
.alert-dot {
  width: 12%;
  height: 12%;
  background: currentColor;
  border-radius: 50%;
  margin-top: 4rpx;
}

/* ====== 箭头 arrow ====== */
.ico-arrow {
  justify-content: flex-end;
}
.arrow-line {
  position: absolute;
  width: 62%;
  height: 4rpx;
  background: currentColor;
  border-radius: 2rpx;
  right: 16%;
  opacity: 0.6;
}
.arrow-head {
  width: 0;
  height: 0;
  border-top: 9rpx solid transparent;
  border-bottom: 9rpx solid transparent;
  border-left: 14rpx solid currentColor;
  margin-left: 30%;
}

/* ====== 微信 wechat ====== */
.ico-wechat {
  align-items: flex-start;
  padding-top: 6%;
}
.bubble-big {
  position: relative;
  width: 58%;
  height: 46%;
  border: 4rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8%;
}
.bubble-small {
  position: absolute;
  right: 4%;
  bottom: 12%;
  width: 36%;
  height: 30%;
  background: var(--color-card, #ffffff);
  border: 4rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10%;
}
.eye {
  width: 12%;
  height: 18%;
  min-width: 4rpx;
  min-height: 4rpx;
  border-radius: 50%;
  background: currentColor;
}

/* ====== 首页 home（房子） ====== */
.ico-home {
  flex-direction: column;
  justify-content: flex-end;
}
.home-roof {
  width: 0;
  height: 0;
  border-left: 30% solid transparent;
  border-right: 30% solid transparent;
  border-bottom: 26% solid currentColor;
  /* 屋顶居中：三角形宽度依赖 border，用 flex 居中即可 */
}
.home-body {
  position: relative;
  width: 62%;
  height: 38%;
  background: currentColor;
  border-radius: 4rpx 4rpx 8rpx 8rpx;
  margin-top: -2rpx;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.home-door {
  width: 26%;
  height: 55%;
  background: var(--color-card, #ffffff);
  border-radius: 4rpx 4rpx 0 0;
}

/* ====== 我的 mine（人形） ====== */
.ico-mine {
  flex-direction: column;
  justify-content: flex-end;
  padding-bottom: 6%;
}
.mine-head {
  width: 34%;
  height: 30%;
  border-radius: 50%;
  background: currentColor;
}
.mine-body {
  width: 66%;
  height: 34%;
  border-radius: 999rpx 999rpx 0 0;
  background: currentColor;
  margin-top: -2rpx;
  opacity: 0.85;
}

/* ====== 银行/产品 bank（柱廊） ====== */
.ico-bank {
  flex-direction: column;
  justify-content: flex-end;
  padding-bottom: 4%;
}
.bank-beam {
  width: 92%;
  height: 12%;
  background: currentColor;
  border-radius: 3rpx;
}
.bank-cols {
  display: flex;
  justify-content: space-between;
  width: 82%;
  margin-top: 8%;
}
.bank-col {
  width: 20%;
  height: 34%;
  background: currentColor;
  border-radius: 3rpx 3rpx 0 0;
  opacity: 0.85;
}

/* ====== 服务单 order（单据） ====== */
.ico-order {
  flex-direction: column;
  justify-content: flex-end;
}
.order-frame {
  width: 72%;
  height: 78%;
  border: 4rpx solid currentColor;
  border-radius: 8rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12%;
  padding-top: 4%;
}
.order-line {
  width: 62%;
  height: 4rpx;
  background: currentColor;
  border-radius: 2rpx;
  opacity: 0.8;
}
.order-line-short {
  width: 42%;
  opacity: 0.5;
}

/* ====== 刷新 refresh（环形箭头） ====== */
.ico-refresh {
  position: relative;
}
.refresh-arc {
  position: absolute;
  width: 76%;
  height: 76%;
  border-top: 4rpx solid currentColor;
  border-right: 4rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
  top: 8%;
  left: 6%;
}
.refresh-head {
  position: absolute;
  width: 0;
  height: 0;
  border-top: 8rpx solid transparent;
  border-bottom: 8rpx solid transparent;
  border-left: 12rpx solid currentColor;
  top: 16%;
  right: 2%;
  transform: rotate(-12deg);
}

/* ====== 工作台 workbench（田字格） ====== */
.ico-workbench {
  flex-wrap: wrap;
  gap: 10%;
  padding: 4%;
}
.wb-cell {
  width: 38%;
  height: 38%;
  background: currentColor;
  border-radius: 6rpx;
}
.wb-cell:nth-child(odd) {
  opacity: 1;
}
.wb-cell:nth-child(even) {
  opacity: 0.55;
}

/* ====== 用户/线索 users（双人） ====== */
.ico-users {
  position: relative;
  padding-bottom: 6%;
}
.user {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.user-front {
  right: 6%;
  bottom: 0;
  width: 52%;
  height: 74%;
}
.user-back {
  left: 6%;
  bottom: 0;
  width: 44%;
  height: 60%;
  opacity: 0.55;
}
.user-head {
  width: 42%;
  height: 30%;
  border-radius: 50%;
  background: currentColor;
}
.user-body {
  width: 100%;
  height: 46%;
  border-radius: 999rpx 999rpx 0 0;
  background: currentColor;
  margin-top: -2rpx;
}

/* ====== 企业 enterprise（办公楼） ====== */
.ico-enterprise { align-items: flex-end; }
.enterprise-building {
  width: 72%; height: 84%; border: 4rpx solid currentColor; border-radius: 6rpx 6rpx 2rpx 2rpx;
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 10%; padding: 12%; box-sizing: border-box;
}
.enterprise-window { background: currentColor; border-radius: 2rpx; opacity: .72; }

/* ====== 个人 person（单人轮廓） ====== */
.ico-person { flex-direction: column; justify-content: flex-end; }
.person-head { width: 34%; height: 34%; border: 4rpx solid currentColor; border-radius: 50%; box-sizing: border-box; }
.person-body { width: 76%; height: 42%; margin-top: 6%; border: 4rpx solid currentColor; border-bottom: 0; border-radius: 999rpx 999rpx 0 0; box-sizing: border-box; }

/* ====== 趋势 trend（折线） ====== */
.ico-trend {
  align-items: flex-end;
  padding: 10% 6% 6%;
}
.trend-axis {
  position: absolute;
  left: 8%;
  right: 8%;
  bottom: 12%;
  height: 4rpx;
  background: currentColor;
  opacity: 0.5;
}
.trend-line {
  position: absolute;
  left: 14%;
  right: 14%;
  bottom: 18%;
  height: 4rpx;
  background: currentColor;
  transform: rotate(-12deg);
  transform-origin: left center;
  opacity: 0.9;
}
.trend-dot {
  position: absolute;
  width: 14%;
  height: 14%;
  border-radius: 50%;
  background: currentColor;
}
.trend-dot-1 { left: 10%; bottom: 16%; }
.trend-dot-2 { left: 44%; bottom: 32%; }
.trend-dot-3 { left: 78%; bottom: 46%; }

/* ====== 文档 doc（纸张+行） ====== */
.ico-doc {
  justify-content: flex-end;
}
.doc-sheet {
  position: relative;
  width: 68%;
  height: 82%;
  background: currentColor;
  border-radius: 4rpx;
  opacity: 0.95;
}
.doc-line {
  position: absolute;
  left: 22%;
  height: 5rpx;
  border-radius: 3rpx;
  background: var(--bg-card, #fff);
  opacity: 0.85;
}
.doc-line-1 { top: 28%; width: 46%; }
.doc-line-2 { top: 46%; width: 34%; }

/* ====== 文件 file（折叠角） ====== */
.ico-file {
  justify-content: flex-end;
}
.file-body {
  position: relative;
  width: 64%;
  height: 80%;
  background: currentColor;
  border-radius: 4rpx;
  opacity: 0.95;
}
.file-fold {
  position: absolute;
  right: 0;
  top: 0;
  width: 30%;
  height: 30%;
  background: var(--bg-card, #fff);
  border-bottom-left-radius: 6rpx;
  opacity: 0.9;
}

/* ====== 照片 photo（相框+山+太阳） ====== */
.ico-photo {
  padding: 6%;
}
.photo-frame {
  position: relative;
  width: 88%;
  height: 88%;
  border: 5rpx solid currentColor;
  border-radius: 6rpx;
  overflow: hidden;
}
.photo-sun {
  position: absolute;
  right: 14%;
  top: 14%;
  width: 20%;
  height: 20%;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.9;
}
.photo-mountain {
  position: absolute;
  left: 8%;
  bottom: -6%;
  width: 60%;
  height: 52%;
  background: currentColor;
  transform: rotate(45deg);
  opacity: 0.85;
}

/* ====== 顾问服务 support（耳麦） ====== */
.ico-support { position: relative; }
.support-band {
  position: absolute;
  width: 64%;
  height: 58%;
  top: 12%;
  border: 5rpx solid currentColor;
  border-bottom: 0;
  border-radius: 999rpx 999rpx 0 0;
  box-sizing: border-box;
}
.support-ear {
  position: absolute;
  top: 48%;
  width: 16%;
  height: 28%;
  border-radius: 6rpx;
  background: currentColor;
}
.support-ear-left { left: 10%; }
.support-ear-right { right: 10%; }
.support-mic {
  position: absolute;
  right: 18%;
  bottom: 8%;
  width: 30%;
  height: 5rpx;
  border-radius: 4rpx;
  background: currentColor;
  transform: rotate(-18deg);
  transform-origin: right center;
}

/* ====== 分享 share（三点连线） ====== */
.ico-share { position: relative; }
.share-line {
  position: absolute;
  left: 30%;
  width: 44%;
  height: 4rpx;
  border-radius: 3rpx;
  background: currentColor;
  transform-origin: left center;
}
.share-line-top { top: 45%; transform: rotate(-28deg); }
.share-line-bottom { bottom: 44%; transform: rotate(28deg); }
.share-node {
  position: absolute;
  width: 24%;
  height: 24%;
  border: 4rpx solid currentColor;
  border-radius: 50%;
  background: var(--bg-card, #fff);
  box-sizing: border-box;
}
.share-node-left { left: 4%; top: 38%; }
.share-node-top { right: 4%; top: 4%; }
.share-node-bottom { right: 4%; bottom: 4%; }

/* ====== 搜索 search（放大镜） ====== */
.ico-search { position: relative; }
.search-ring {
  position: absolute;
  width: 60%;
  height: 60%;
  left: 4%;
  top: 4%;
  border: 4rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}
.search-handle {
  position: absolute;
  width: 30%;
  height: 5rpx;
  background: currentColor;
  border-radius: 3rpx;
  right: 2%;
  bottom: 12%;
  transform: rotate(-45deg);
  transform-origin: right center;
}

/* ====== 兜底圆点 ====== */
.ico-default {
  width: 30%;
  height: 30%;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
}
</style>
