<template>
  <view class="wrapper-page theme-root" :data-theme="themeMode">
    <LeadEntryProduct :active="true" />
    <TabBar current="luru" />
  </view>
</template>

<script setup>
/**
 * 兼容壳页：原「录入产品 / 我的产品」tab 已并入「线索录入」合并页（D74）。
 *
 * <p>保留本路由是为了不打断既有跳转（如产品编辑页返回、「我的」页业务录入入口）。
 * 页面本身不再承载业务逻辑，仅渲染 {@code LeadEntryProduct} 子视图。
 */
import TabBar from '../../components/TabBar.vue';
import LeadEntryProduct from '../../components/LeadEntryProduct.vue';
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
