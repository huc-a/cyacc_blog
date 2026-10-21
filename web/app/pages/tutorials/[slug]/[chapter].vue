<script setup lang="ts">
import type { Content } from '~/types'
const route = useRoute()
const { data, error } = await useFetch<Content>(`/api/public/contents/${encodeURIComponent(String(route.params.slug))}`)
if (error.value || !data.value || data.value.contentType !== 'tutorial') throw createError({ statusCode: 404, message: '教程不存在' })
</script>
<template><ContentReader v-if="data" :item="data" :chapter-slug="String(route.params.chapter)" /></template>
