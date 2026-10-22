<script setup lang="ts">
import type { Content, Page, Taxon } from '~/types'
import { sections } from '~/utils/site'
const route = useRoute()
const section = computed(() => sections.find(s => s.path === route.params.section))
if (!section.value) throw createError({ statusCode: 404, message: '页面不存在' })
const q = ref(String(route.query.q || '')), searchOpen = ref(!!route.query.q)
watch(() => route.query.q, value => { q.value = String(value || '') })
watch(() => route.params.section, () => { searchOpen.value = !!route.query.q })
const query = computed(() => ({ type: section.value!.type, q: route.query.q || '', page: Number(route.query.page || 1), categoryId: route.query.categoryId, size: 9 }))
const { data, error, refresh } = await useFetch<Page<Content>>('/api/public/contents', { query, key: `section-${route.params.section}` })
const { data: taxonomy } = await useFetch<{ categories: Taxon[] }>('/api/public/taxonomy')
const categories = computed(() => taxonomy.value?.categories.filter(c => c.contentType === section.value?.type && c.count) || [])
function filter(key: string, value?: string | number) { navigateTo({ path: route.path, query: { ...route.query, [key]: value || undefined, ...(key === 'page' ? {} : { page: undefined }) } }) }
useSeoMeta({ title: () => section.value?.label, description: () => section.value?.description })
</script>
<template><div v-if="section" class="wrap collection"><header class="collection-hero"><span class="eyebrow">{{ section.eyebrow }}</span><h1>{{ section.headline }}</h1><p>{{ section.description }}</p></header><div class="collection-toolbar glass"><div class="filter-pills"><button :class="{ selected: !route.query.categoryId }" @click="filter('categoryId')">全部{{ section.label }}</button><button v-for="c in categories" :key="c.id" :class="{ selected: String(route.query.categoryId) === String(c.id) }" @click="filter('categoryId', c.id)">{{ c.name }}</button></div><InlineSearch v-model="q" v-model:open="searchOpen" class="collection-search" :field-label="`搜索${section.label}`" :search-label="`${section.label}内搜索`" allow-empty :collapse-on-submit="false" @search="filter('q', $event)" /></div><div v-if="route.query.q" class="collection-search-state">关键词：{{ route.query.q }}<button class="quiet" @click="filter('q')">清除搜索</button></div><div v-if="error" class="glass empty-state" role="alert"><p>内容加载失败，请稍后重试。</p><button @click="refresh()">重新加载</button></div><div v-else class="collection-grid"><ContentCard v-for="item in data?.items" :key="item.id" :item="item" compact /></div><div v-if="!error && !data?.items.length" class="glass empty-state"><span class="empty-symbol">{{ section.icon }}</span><h2>这里还没有公开的{{ section.label }}。</h2><p>留一处空白，等待新的发现。</p><NuxtLink to="/">返回首页 →</NuxtLink></div><Pagination :page="query.page" :total="data?.total || 0" :size="9" @change="filter('page', $event)" /></div></template>
