<script setup lang="ts">
import { defaultSite, themeStyle } from '~/utils/site'
definePageMeta({ layout: false })
const route = useRoute(); const api = useApi(); const { load } = useAuth()
const username = ref('admin'), password = ref(''), error = ref(''), busy = ref(false)
useSeoMeta({ title: '管理员登录 · 菜牙爱吃菜', robots: 'noindex,nofollow' })
async function submit() {
  busy.value = true; error.value = ''
  try {
    await api('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: username.value, password: password.value }), headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
    await load(); const target = String(route.query.redirect || '/admin/dashboard')
    await navigateTo(target.startsWith('/admin/') && !target.startsWith('/admin/login') ? target : '/admin/dashboard')
  } catch (e) { error.value = errorText(e) } finally { busy.value = false; password.value = '' }
}
</script>
<template><div class="login-screen" :style="themeStyle(defaultSite)"><div class="landscape" /><NuxtLink to="/" class="login-back">← 回到博客</NuxtLink><section class="login-card glass"><div class="avatar">菜</div><span class="eyebrow">A LITTLE SPACE FOR YOUR IDEAS</span><h1>欢迎回到创作的小天地。</h1><p>登录后，继续那些值得记录的想法。</p><form @submit.prevent="submit"><label for="username">管理员账号</label><input id="username" v-model="username" autocomplete="username" required maxlength="60"><label for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="current-password" required maxlength="72"><p v-if="error" role="alert" class="form-error">{{ error }}</p><button class="primary" :disabled="busy" type="submit">{{ busy ? '正在登录…' : '登录管理后台' }}</button></form><small>WRITE A LITTLE. GROW A LITTLE.</small></section></div></template>
