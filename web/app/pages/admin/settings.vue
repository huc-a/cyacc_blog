<script setup lang="ts">
import type { SiteSettings, Page, Content } from '~/types'
import { useMessage } from 'naive-ui'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '系统设置' })
const api = useApi(), message = useMessage()
const loaded = await api<{ settings: SiteSettings; lockVersion: number }>('/api/admin/site')
const settings = ref(loaded.settings), version = ref(loaded.lockVersion), snapshot = ref(JSON.stringify(loaded.settings)), busy = ref(false)
const uploadingWallpaper = ref(false), uploadingDefaultCover = ref(false)
const siteDirty = computed(() => snapshot.value !== JSON.stringify(settings.value))
const dirty = computed(() => siteDirty.value || uploadingWallpaper.value || uploadingDefaultCover.value)
useUnsavedChanges(dirty)
async function save() {
  busy.value = true
  try {
    const result = await api<typeof loaded>('/api/admin/site', { method: 'PUT', body: { settings: settings.value, lockVersion: version.value } })
    version.value = result.lockVersion; snapshot.value = JSON.stringify(settings.value)
    await refreshNuxtData('site-settings'); message.success('系统设置已保存')
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
async function exportContents() {
  busy.value = true
  try {
    const items: Content[] = []
    for (const status of ['all', 'trash']) {
      let page = 1, total = 0
      do {
        const result = await api<Page<Content>>(`/api/admin/contents?status=${status}&page=${page}&size=50`)
        total = result.total
        for (const item of result.items) items.push(await api(`/api/admin/contents/${item.id}`))
        page++
      } while ((page - 1) * 50 < total)
    }
    const payload = { exportedAt: new Date().toISOString(), format: 'caiya-content-export-v1', note: 'Current working revisions only; media binaries and frozen history require a full database and media backup.', site: (await api<typeof loaded>('/api/admin/site')).settings, taxonomy: await api('/api/admin/taxonomy'), contents: items }
    const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a'); link.href = url; link.download = `caiya-content-${new Date().toISOString().slice(0, 10)}.json`; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000); message.success('内容导出已开始下载')
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
</script>
<template>
  <div>
    <AdminPageHead title="系统设置" subtitle="背景、登录页壁纸、默认封面和数据导出。站点名称、作者介绍在「网站信息」里维护。">
      <button class="primary" :disabled="busy || uploadingWallpaper || uploadingDefaultCover || !siteDirty" @click="save">保存设置</button>
    </AdminPageHead>
    <div style="max-width:880px">
      <section class="studio-panel">
        <h2>背景</h2>
        
        <SiteImageControl v-model="settings.backgroundAssetId" kind="wallpaper" label="自定义背景图" save-hint="保存设置后生效" />
        <p class="admin-footnote">全站背景只用自定义图片；不设置时使用默认素色底。</p>
        <div class="settings-divider" />
        <h2>登录页壁纸</h2>
        <SiteImageControl v-model="settings.loginBackgroundAssetId" v-model:busy="uploadingWallpaper" kind="wallpaper" label="登录页壁纸" save-hint="保存设置后生效" />
        <p class="admin-footnote">未选择时沿用博客背景；登录页对所有访客可见。</p>
        <div class="settings-divider" />
        <h2>默认文章封面</h2>
        <SiteImageControl v-model="settings.defaultCoverAssetId" v-model:busy="uploadingDefaultCover" kind="cover" label="默认文章封面" save-hint="保存设置后应用于未单独设置封面的内容" />
        <p class="admin-footnote">内容未设置封面时会使用这张图片。</p>
        <div class="settings-divider" />
        <h2>数据导出与备份</h2>
        <p class="admin-footnote">导出当前工作版本、标签和网站设置，便于整理内容。写文章时可以在新建文章页把 Markdown 文件直接载入编辑器。图片文件和历史版本需要随数据库一起备份。</p>
        <button :disabled="busy" @click="exportContents">导出内容 JSON</button>
        <div class="help-note">完整备份与恢复：使用项目提供的备份脚本，同时保存数据库与上传目录。恢复前先在隔离环境验证。</div>
      </section>
      
    </div>
  </div>
</template>
