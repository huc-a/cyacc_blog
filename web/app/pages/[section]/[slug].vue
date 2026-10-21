<script setup lang="ts">
import type { Content } from '~/types'
import { sections } from '~/utils/site'
const route = useRoute()
const { data, error } = await useFetch<Content>(`/api/public/contents/${encodeURIComponent(String(route.params.slug))}`)
if (error.value || !data.value) throw createError({ statusCode: error.value?.statusCode || 404, message: '内容不存在或尚未发布' })
if (!sections.some(s => s.path === route.params.section && s.type === data.value?.contentType)) throw createError({ statusCode: 404, message: '内容不存在' })
</script>
<template><ContentReader v-if="data" :item="data" /></template>
