<script setup lang="ts">
import type { SiteSettings } from '~/types'
import { useMessage } from 'naive-ui'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '网站信息' })
const api = useApi(), message = useMessage()
const loaded = await api<{ settings: SiteSettings; lockVersion: number }>('/api/admin/site')
const settings = ref<SiteSettings>(loaded.settings), version = ref(loaded.lockVersion), snapshot = ref(JSON.stringify(loaded.settings))
const dirty = computed(() => snapshot.value !== JSON.stringify(settings.value)), busy = ref(false), uploadingAvatar = ref(false)
useUnsavedChanges(dirty)
async function save() {
  busy.value = true
  try {
    const result = await api<typeof loaded>('/api/admin/site', { method: 'PUT', body: { settings: settings.value, lockVersion: version.value } })
    version.value = result.lockVersion; snapshot.value = JSON.stringify(settings.value)
    await refreshNuxtData('site-settings'); message.success('网站信息已保存')
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
</script>
<template>
  <div style="max-width:780px">
    <AdminPageHead title="网站信息" subtitle="站点叫什么、作者是谁、访客怎么联系你。">
      <button class="primary" :disabled="busy || uploadingAvatar || !dirty" @click="save">保存</button>
    </AdminPageHead>
    <section class="studio-panel">
      <div class="two-fields">
        <div class="field"><label for="site-name">站点名称</label><input id="site-name" v-model="settings.name" maxlength="80"></div>
        <div class="field"><label for="site-author">作者名称</label><input id="site-author" v-model="settings.author" maxlength="80"></div>
      </div>
      <div class="two-fields">
        <div class="field"><label for="site-email">联系邮箱</label><input id="site-email" v-model="settings.email" type="email" maxlength="254"></div>
        <div class="field"><label for="site-github">GitHub 地址</label><input id="site-github" v-model="settings.github" type="url" placeholder="https://github.com/…"></div>
      </div>
      <div class="field"><label for="site-description">站点描述</label><textarea id="site-description" v-model="settings.description" maxlength="300" rows="2" /></div>
      <div class="field" style="margin-bottom:0"><label for="site-bio">关于我</label><textarea id="site-bio" v-model="settings.bio" maxlength="500" rows="3" /></div>
    </section>
    <section class="studio-panel" style="margin-top:20px">
      <h2>头像与页脚</h2>
      <div class="field"><label>站点头像</label><SiteImageControl v-model="settings.avatarAssetId" v-model:busy="uploadingAvatar" kind="avatar" /></div>
      <div class="field"><label for="site-footer">页脚文案</label><input id="site-footer" v-model="settings.footer" maxlength="300"></div>
      <div class="two-fields">
        <div class="field" style="margin-bottom:0"><label for="site-icp">ICP 备案号</label><input id="site-icp" v-model="settings.icpNumber" maxlength="80" placeholder="如：桂ICP备2026024037号"></div>
        <div class="field" style="margin-bottom:0"><label for="site-police">公安备案号</label><input id="site-police" v-model="settings.policeNumber" maxlength="80" placeholder="未取得可留空"></div>
      </div>
      <p class="admin-footnote">备案号填写后显示在前台页脚并链接到官方查询；公安备案号留空时显示“备案办理中”。</p>
    </section>
    <p class="admin-footnote" style="margin-top:16px">背景、登录壁纸、默认封面在 <NuxtLink to="/admin/settings">系统设置</NuxtLink> 里；登录账号的头像和显示名在 <NuxtLink to="/admin/profile">管理员资料</NuxtLink> 里，两者互不影响。</p>
  </div>
</template>
