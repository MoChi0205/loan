<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>企业贷款咨询服务 · 管理端</h2>
      <el-form :model="form">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" @click="onLogin">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

/** 登录表单（骨架，正式开发接入 RSA + 验证码） */
const form = reactive({ username: '', password: '' });

/** 登录（骨架：正式开发走 /api/auth/login） */
function onLogin() {
  router.push('/');
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f5f7fa;
}
.login-card {
  width: 400px;
}
.login-btn {
  width: 100%;
}
</style>
