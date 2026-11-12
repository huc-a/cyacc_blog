<script setup lang="ts">
import type { MediaAsset } from '~/types'
import { useMessage } from 'naive-ui'
const props = defineProps<{ kind: 'wallpaper' | 'avatar' | 'cover'; label?: string; saveHint?: string }>()
const asset = defineModel<number | null>({ required: true })
const message = useMessage(), api = useApi(), input = ref<HTMLInputElement | null>(null), picker = ref(false)
const busy = defineModel<boolean>('busy', { default: false })
const label = computed(() => props.label || (props.kind === 'avatar' ? '头像' : props.kind === 'cover' ? '封面' : '壁纸'))
async function upload(files: FileList | null) {
  const file = files?.[0]; if (!file) return
  busy.value = true
  try {
    if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024) { message.error('请选择 5 MB 以内的 PNG、JPEG 或 GIF'); return }
    const body = new FormData(); body.append('file', file)
    const result = await api<MediaAsset>('/api/admin/media', { method: 'POST', body }); asset.value = result.id
    message.success(`${label.value}已上传，${props.saveHint || '应用更改后会显示到前台'}`)
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false; if (input.value) input.value.value = '' }
}
</script>
<template><div class="site-image-control"><img v-if="asset" :src="`/api/admin/media/${asset}/file`" :alt="`自定义${label}预览`" :class="kind === 'avatar' ? 'avatar-control-preview' : 'background-thumb'"><div v-else class="image-empty">{{ kind === 'avatar' ? '选择一张喜欢的画面，GIF 会保留动画。' : '上传自己的壁纸，铺满整个页面背景。' }}</div><input ref="input" type="file" class="sr-only" accept="image/png,image/jpeg,image/gif" :aria-label="`上传自定义${label}`" @change="upload(($event.target as HTMLInputElement).files)"><div class="admin-actions"><button type="button" :disabled="busy" @click="input?.click()">{{ busy ? '上传中…' : `上传${label}` }}</button><button type="button" :disabled="busy" @click="picker = true">{{ props.label ? `从媒体库选择${label}` : kind === 'wallpaper' ? '从媒体库选择背景' : '从媒体库选择头像' }}</button><button v-if="asset" type="button" :disabled="busy" @click="asset = null">移除{{ label }}</button></div><p class="admin-footnote">PNG / JPEG / GIF · 每张不超过 5 MB</p><MediaPicker v-model:show="picker" @select="asset = $event.id" /></div></template>
