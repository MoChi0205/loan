<template>
  <view class="wrapper-page theme-root" :data-theme="themeMode">
    <LeadEntryClient :active="true" />
    <TabBar current="luru" />
  </view>
</template>

<script setup>
/**
 * 兼容壳页：原「录入线索」tab 已并入「线索录入」合并页（D74）。
 *
 * <p>保留本路由是为了不打断既有跳转（如「我的」页业务录入入口、H5 深链）。
 * 页面本身不再承载业务逻辑，仅渲染 {@code LeadEntryClient} 子视图。
 */
import TabBar from '../../components/TabBar.vue';
import LeadEntryClient from '../../components/LeadEntryClient.vue';
import { useThemeMode } from '../../theme';

const themeMode = useThemeMode();
</script>

<style scoped>
.wrapper-page {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
}
</style>
