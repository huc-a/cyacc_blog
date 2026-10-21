<script setup lang="ts">
import type { Content, Page } from '~/types'
const route = useRoute()
const q = ref(String(route.query.q || ''))
watch(() => route.query.q, value => { q.value = String(value || '') })
const query = computed(() => ({ q: String(route.query.q || ''), page: Number(route.query.page || 1), size: 10 }))
const { data, error } = await useFetch<Page<Content>>('/api/public/contents', { query, key: 'search-contents' })
const search = () => navigateTo({ path: '/search', query: { q: q.value.trim() || undefined } })
useSeoMeta({ title: '搜索', robots: 'noindex,follow' })
</script>
<template><div class="wrap search-page"><header class="collection-hero"><span class="eyebrow">FIND A LITTLE INSPIRATION</span><h1>寻找散落的灵感。</h1><p>按标题、摘要或标签，重新遇见一段记录。</p></header><form class="search-form glass" @submit.prevent="search"><span aria-hidden="true">⌕</span><input v-model="q" type="search" aria-label="搜索内容" maxlength="200" placeholder="输入关键词…"><button class="primary" type="submit">搜索</button></form><p class="search-count">{{ query.q ? `关于「${query.q}」` : '全部公开记录' }} · {{ data?.total || 0 }} 条</p><p v-if="error" role="alert" class="glass empty-state">搜索服务暂时不可用。</p><ContentCard v-for="item in data?.items" :key="item.id" :item="item" /><div v-if="!error && !data?.items.length" class="glass empty-state"><h2>暂时没有找到相关记录。</h2><p>换一个关键词，或去首页随意看看。</p><NuxtLink to="/">回到首页 →</NuxtLink></div><Pagination :page="query.page" :total="data?.total || 0" :size="10" @change="navigateTo({ path: '/search', query: { ...route.query, page: $event } })" /></div></template>
