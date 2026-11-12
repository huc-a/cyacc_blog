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
  <div>
    <AdminPageHead title="网站信息" subtitle="站点叫什么、作者是谁、访客怎么联系你，都在这里维护。">
      <button class="primary" :disabled="busy || uploadingAvatar || !dirty" @click="save">保存网站信息</button>
    </AdminPageHead>
    <div class="settings-grid">
      <section class="studio-panel">
        <h2>基本信息</h2>
        <div class="field"><label for="site-name">站点名称</label><input id="site-name" v-model="settings.name" maxlength="80"></div>
        <div class="field"><label for="site-description">站点描述</label><textarea id="site-description" v-model="settings.description" maxlength="300" rows="3" /></div>
        <div class="two-fields">
          <div class="field"><label for="site-author">作者名称</label><input id="site-author" v-model="settings.author" maxlength="80"></div>
          <div class="field"><label for="site-email">联系邮箱</label><input id="site-email" v-model="settings.email" type="email" maxlength="254"></div>
        </div>
        <div class="field"><label for="site-bio">关于我</label><textarea id="site-bio" v-model="settings.bio" maxlength="500" rows="4" /></div>
        <div class="field"><label for="site-github">GitHub 地址</label><input id="site-github" v-model="settings.github" type="url" placeholder="https://github.com/…"></div>
        <div class="settings-divider" />
        <h2>页脚与备案</h2>
        <div class="field"><label for="site-footer">页脚文案</label><input id="site-footer" v-model="settings.footer" maxlength="300"></div>
        <div class="field"><label for="site-icp">ICP 备案号</label><input id="site-icp" v-model="settings.icpNumber" maxlength="80" placeholder="如：桂ICP备2026024037号">
          <p class="admin-footnote">填写后显示在页脚，并链接到工信部备案查询。</p>
        </div>
        <div class="field"><label for="site-police">公安备案号</label><input id="site-police" v-model="settings.policeNumber" maxlength="80" placeholder="暂未取得，留空显示办理中">
          <p class="admin-footnote">未填写时，前台显示“公安备案办理中”。取得后填写完整号码，将链接到公安机关备案查询。</p>
        </div>
      </section>
      <aside class="settings-stack">
        <section class="studio-panel">
          <h2>站点头像</h2>
          <SiteImageControl v-model="settings.avatarAssetId" v-model:busy="uploadingAvatar" kind="avatar" />
          <p class="admin-footnote">显示在前台侧栏和页脚，是访客最先看到的你。</p>
        </section>
        <section class="studio-panel">
          <h2>相关设置</h2>
          <NuxtLink to="/admin/appearance" class="text-link">外观与背景 →</NuxtLink>
          <NuxtLink to="/admin/settings" class="text-link">系统设置（登录壁纸 / 默认封面）→</NuxtLink>
          <NuxtLink to="/admin/profile" class="text-link">管理员资料（登录账号）→</NuxtLink>
        </section>
      </aside>
    </div>
  </div>
</template>
