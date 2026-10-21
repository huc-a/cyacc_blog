<script setup lang="ts">
import type { MediaAsset, Page } from '~/types'
import { NModal, useMessage } from 'naive-ui'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '媒体库' })
const api = useApi(), message = useMessage(), confirm = useConfirm()
const page = ref(1), busy = ref(false), picker = ref<HTMLInputElement | null>(null), viewing = ref<MediaAsset | null>(null)
const { data, error, refresh } = await useFetch<Page<MediaAsset>>('/api/admin/media', { query: computed(() => ({ page: page.value, size: 16 })) })
async function upload(files: FileList | File[] | null) {
  if (!files?.length) return
  if (files.length > 10) { message.warning('每批最多上传 10 张图片'); return }
  busy.value = true; let completed = 0
  try { for (const file of Array.from(files)) { if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024) throw new Error('请选择 5 MB 以内的 PNG/JPEG/GIF 图片'); const body = new FormData(); body.append('file', file); await api('/api/admin/media', { method: 'POST', body }); completed++ } message.success(`已上传 ${completed} 张图片`) }
  catch (e) { message.error((e as Error).message === '请选择 5 MB 以内的 PNG/JPEG/GIF 图片' ? (e as Error).message : `${completed ? `已上传 ${completed} 张；` : ''}${errorText(e)}`) }
  finally { busy.value = false; if (picker.value) picker.value.value = ''; await refresh() }
}
async function remove(asset: MediaAsset) {
  if (!await confirm('删除图片？', `确认删除「${asset.originalName}」？已被内容版本或背景引用的图片将被保留。`)) return
  try { await api(`/api/admin/media/${asset.id}`, { method: 'DELETE' }); message.success('图片已删除'); await refresh() } catch (e) { message.error(errorText(e)) }
}
async function copy(asset: MediaAsset) { try { await navigator.clipboard.writeText(`![图片说明](/api/public/media/${asset.id})`); message.success('Markdown 图片引用已复制') } catch { message.error('复制失败，请使用编辑器的图片选择功能') } }
</script>
<template><div><AdminPageHead title="媒体库" subtitle="整理背景和图片，让每次创作更顺手。"><button class="primary" :disabled="busy" @click="picker?.click()">＋ 上传图片</button></AdminPageHead><input ref="picker" class="sr-only" type="file" accept="image/png,image/jpeg,image/gif" multiple aria-label="上传图片文件" @change="upload(($event.target as HTMLInputElement).files)"><div class="media-upload" @dragover.prevent @drop.prevent="upload($event.dataTransfer?.files || null)"><span>◫</span><h2>{{ busy ? '正在收好这些画面…' : '把喜欢的画面，收进素材库' }}</h2><p>PNG / JPEG / GIF · 每张不超过 5 MB · 最多 1600 万像素</p><button :disabled="busy" @click="picker?.click()">选择图片</button></div><p v-if="error" class="form-error" role="alert">媒体加载失败。<button @click="refresh()">重试</button></p><div class="media-grid"><article v-for="asset in data?.items" :key="asset.id" class="media-card studio-panel"><button class="media-image" @click="viewing = asset"><img :src="`/api/admin/media/${asset.id}/file`" :alt="asset.originalName" loading="lazy"></button><h3>{{ asset.originalName }}</h3><small>{{ asset.width }} × {{ asset.height }} · {{ Math.ceil(asset.sizeBytes / 1024) }} KB</small><div class="row-actions"><button @click="viewing = asset">查看</button><button @click="copy(asset)">复制引用</button><button class="danger" @click="remove(asset)">删除</button></div></article></div><div v-if="!error && !data?.items.length" class="admin-empty studio-panel">还没有素材，上传第一张图片吧。</div><Pagination :page="page" :total="data?.total || 0" :size="16" @change="page = $event" /><NModal :show="!!viewing" @update:show="value => { if (!value) viewing = null }" preset="card" :title="viewing?.originalName" style="width:min(900px,calc(100% - 30px))"><img v-if="viewing" :src="`/api/admin/media/${viewing.id}/file`" :alt="viewing.originalName" style="max-height:70vh;display:block;margin:auto"></NModal></div></template>
