<script setup lang="ts">
import type { MediaAsset, Page } from '~/types'
import { NModal } from 'naive-ui'
const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ select: [asset: MediaAsset] }>()
const page = ref(1)
const { data, error, refresh } = await useFetch<Page<MediaAsset>>('/api/admin/media', { query: computed(() => ({ page: page.value, size: 12 })), key: `media-picker-${useId()}` })
watch(show, value => { if (value) refresh() })
function select(asset: MediaAsset) { emit('select', asset); show.value = false }
</script>
<template><NModal v-model:show="show" preset="card" title="选择媒体素材" class="media-picker" style="width:min(850px,calc(100% - 30px))"><p class="admin-footnote">选择已上传的图片。需要新素材时，可先前往媒体库上传。</p><p v-if="error" class="form-error">媒体加载失败。<button @click="refresh()">重试</button></p><div class="picker-grid"><button v-for="asset in data?.items" :key="asset.id" @click="select(asset)"><img :src="`/api/admin/media/${asset.id}/file`" :alt="asset.originalName"><span>{{ asset.originalName }}</span></button></div><div v-if="!data?.items.length" class="admin-empty">媒体库还是空的。<NuxtLink to="/admin/media">去上传一张图片 →</NuxtLink></div><Pagination :page="page" :total="data?.total || 0" :size="12" @change="page = $event" /></NModal></template>
