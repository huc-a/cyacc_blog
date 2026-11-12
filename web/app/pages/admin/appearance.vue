<script setup lang="ts">
import type { SiteSettings } from '~/types'
import { useMessage } from 'naive-ui'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '外观' })
const api = useApi(), message = useMessage()
const loaded = await api<{ settings: SiteSettings; lockVersion: number }>('/api/admin/site')
const settings = ref<SiteSettings>(loaded.settings), version = ref(loaded.lockVersion), snapshot = ref(JSON.stringify(loaded.settings))
const dirty = computed(() => snapshot.value !== JSON.stringify(settings.value)), busy = ref(false)
useUnsavedChanges(dirty)
async function save() {
  busy.value = true
  try {
    const result = await api<typeof loaded>('/api/admin/site', { method: 'PUT', body: { settings: settings.value, lockVersion: version.value } })
    version.value = result.lockVersion; snapshot.value = JSON.stringify(settings.value)
    await refreshNuxtData('site-settings'); message.success('背景已应用到前台')
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
</script>
<template>
  <div>
    <AdminPageHead title="外观" subtitle="样式已经固定成现在的样子，平时不用调。这里只留一件事：换背景。">
      <button class="primary" :disabled="busy || !dirty" @click="save">应用背景</button>
    </AdminPageHead>
    <section class="studio-panel" style="max-width:640px">
      <h2>背景色调</h2>
      <div class="scene-options">
        <button v-for="scene in [{ id: 'mist', name: '雾山' }, { id: 'blue', name: '青岚' }, { id: 'dusk', name: '暮色' }]" :key="scene.id" :class="{ active: settings.background === scene.id && !settings.backgroundAssetId }" @click="settings.background = scene.id as SiteSettings['background']; settings.backgroundAssetId = null">
          <img :src="`/backgrounds/${scene.id}.svg`" :alt="scene.name"><span>{{ scene.name }}</span>
        </button>
      </div>
      <div class="settings-divider" />
      <h2>自定义背景图</h2>
      <SiteImageControl v-model="settings.backgroundAssetId" kind="wallpaper" />
      <p class="admin-footnote">上传或选择一张图片作为全站背景；选了自定义图后，色调预设暂时不生效，移除图片即可恢复。</p>
    </section>
  </div>
</template>
