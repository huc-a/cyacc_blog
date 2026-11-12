<script setup lang="ts">
import { defaultSite, themeStyle } from '~/utils/site'
definePageMeta({ layout: false })
const route = useRoute(); const api = useApi(); const { load } = useAuth()
const { data: site } = await useSite()
const appearance = computed(() => {
  const value = site.value || defaultSite
  return { ...value, backgroundAssetId: value.loginBackgroundAssetId || value.backgroundAssetId }
})
const username = ref(''), password = ref(''), error = ref(''), busy = ref(false)
const captchaRequired = ref(true), captchaAnswer = ref(''), captchaImage = ref(''), captchaBusy = ref(false), captchaError = ref(''), retryAfter = ref(0)
let countdown: ReturnType<typeof setInterval> | undefined
onMounted(() => { countdown = setInterval(() => { if (retryAfter.value > 0) retryAfter.value-- }, 1000); void refreshCaptcha() })
onBeforeUnmount(() => { if (countdown) clearInterval(countdown) })
watch(username, () => { retryAfter.value = 0; error.value = '' })
async function refreshCaptcha() {
  if (captchaBusy.value) return
  captchaBusy.value = true; captchaAnswer.value = ''; captchaImage.value = ''; captchaError.value = ''
  try { const result = await api<{ image: string }>('/api/auth/captcha'); captchaImage.value = result.image }
  catch (e) { captchaError.value = errorText(e) } finally { captchaBusy.value = false }
}
useSeoMeta({ title: '管理员登录 · 菜牙爱吃菜', robots: 'noindex,nofollow' })
async function submit() {
  if (busy.value || retryAfter.value > 0 || captchaBusy.value) return
  busy.value = true; error.value = ''
  try {
    await api('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: username.value.trim(), password: password.value, captcha: captchaAnswer.value }), headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
    await load(); const target = String(route.query.redirect || '/admin/dashboard')
    await navigateTo(target.startsWith('/admin/') && !target.startsWith('/admin/login') ? target : '/admin/dashboard')
  } catch (e) {
    error.value = errorText(e)
    const details = (e as { data?: { captchaRequired?: boolean; retryAfter?: number } }).data
    retryAfter.value = Math.min(900, Math.max(0, Number(details?.retryAfter) || 0))
    captchaRequired.value = captchaRequired.value || details?.captchaRequired === true
    captchaAnswer.value = ''; captchaImage.value = ''
    if (captchaRequired.value && retryAfter.value === 0) await refreshCaptcha()
  } finally { busy.value = false; password.value = '' }
}
</script>
<style scoped>
.captcha-field{margin:16px 0}.captcha-row{display:flex;align-items:center;gap:10px}.captcha-row input{min-width:0;width:0;flex:1;margin:0}.login-card .captcha-refresh{padding:0;flex:0 0 152px;width:152px;height:52px;overflow:hidden;border:1px solid #d5deca;background:#f5f6ea;border-radius:8px;margin:0;color:#506b3b}.captcha-refresh img{display:block;width:152px;height:52px}.captcha-field label{margin-bottom:9px}.login-card{margin-top:60px;margin-bottom:35px}
@media(max-width:400px){.login-card .captcha-refresh{flex-basis:126px;width:126px;height:44px}.captcha-refresh img{width:126px;height:44px}.captcha-row input{font-size:13px}}
</style>
<template><div class="login-screen" :style="themeStyle(appearance)"><div class="landscape" /><NuxtLink to="/" class="login-back">← 回到博客</NuxtLink><section class="login-card glass"><div class="avatar">菜</div><span class="eyebrow">A LITTLE SPACE FOR YOUR IDEAS</span><h1>欢迎回到创作的小天地。</h1><p>登录后，继续那些值得记录的想法。</p><form @submit.prevent="submit"><label for="username">管理员账号</label><input id="username" v-model="username" autocomplete="username" autocapitalize="none" spellcheck="false" placeholder="请输入账号" required maxlength="60"><label for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="current-password" required maxlength="72"><div v-if="captchaRequired" class="captcha-field"><label for="captcha">图片验证码</label><div class="captcha-row"><input id="captcha" v-model="captchaAnswer" required maxlength="5" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="不区分大小写" :disabled="busy || captchaBusy"><button type="button" class="captcha-refresh" :disabled="captchaBusy || busy" aria-label="换一张验证码" @click="refreshCaptcha"><img v-if="captchaImage" :src="captchaImage" alt="图片验证码，点击可换一张" width="190" height="64"><span v-else>{{ captchaBusy ? '正在加载…' : '获取验证码' }}</span></button></div><p v-if="captchaError" class="form-error" role="alert">{{ captchaError }}</p></div><p v-if="error" role="alert" class="form-error">{{ error }}</p><button class="primary" :disabled="busy || captchaBusy || retryAfter > 0 || (captchaRequired && !captchaImage)" type="submit">{{ retryAfter > 0 ? `${retryAfter} 秒后重试` : busy ? '正在登录…' : '登录管理后台' }}</button></form><small>WRITE A LITTLE. GROW A LITTLE.</small></section></div></template>
