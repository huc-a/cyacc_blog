<script setup lang="ts">
import type { Content, Page, Taxon } from '~/types'
import { defaultSite, sections, contentUrl, dateText } from '~/utils/site'
const route = useRoute()
const { data: settings } = await useSite()
const site = computed(() => settings.value || defaultSite)
const { data: taxonomy } = await useFetch<{ categories: Taxon[]; tags: Taxon[] }>('/api/public/taxonomy')
const { data: summary } = await useFetch<{ counts: { contents: number; categories: number; tags: number }; calendar: { date: string }[] }>('/api/public/summary')
const query = computed(() => ({ home: true, type: String(route.query.type || ''), categoryId: route.query.categoryId, tagId: route.query.tagId, date: route.query.date, page: Number(route.query.page || 1), size: 6 }))
const { data, status, error, refresh } = await useFetch<Page<Content>>('/api/public/contents', { query, key: 'home-contents' })
const { data: recent } = await useFetch<Page<Content>>('/api/public/contents', { query: { size: 3 }, key: 'recent-publications' })
const categories = computed(() => taxonomy.value?.categories.filter(t => t.count) || [])
const tags = computed(() => taxonomy.value?.tags.filter(t => t.count).slice(0, 18) || [])
const filtered = computed(() => !!(route.query.categoryId || route.query.tagId || route.query.date))
function filter(key: string, value?: string | number) { navigateTo({ path: '/', query: { ...route.query, [key]: value || undefined, ...(key === 'page' ? {} : { page: undefined }) } }) }
useSeoMeta({ title: '首页' })
</script>
<template>
  <div class="wrap">
    <section class="hero"><div><span class="eyebrow">NOTES, CODE & EVERYDAY LIFE</span><h1>{{ site.headline }}</h1><p>{{ site.subtitle }}</p><div class="hero-caption"><span class="tiny-dot" /> 在代码与生活之间，给想法留一个位置。</div></div><aside class="hero-note glass"><span class="eyebrow">A MOMENT OF STILLNESS</span><p>不必着急抵达。<br>在这里，慢慢读。</p><small>EST. 2026 · PERSONAL JOURNAL</small></aside></section>
    <div class="home-grid">
      <aside class="sidebar-stack left-sidebar"><section class="glass profile panel"><div class="avatar">菜</div><span class="eyebrow">HELLO, I'M</span><h2>{{ site.author }}</h2><p>{{ site.bio }}</p><div class="social-links"><a v-if="site.github" :href="site.github" target="_blank" rel="noopener noreferrer">GitHub ↗</a><a v-if="site.email" :href="`mailto:${site.email}`">邮箱 ↗</a><NuxtLink to="/about">关于我 ↗</NuxtLink></div><div class="profile-counts"><div><strong>{{ summary?.counts.contents ?? '—' }}</strong><small>记录</small></div><div><strong>{{ categories.length }}</strong><small>分类</small></div><div><strong>{{ tags.length }}</strong><small>标签</small></div></div></section>
        <section class="glass panel"><h2 class="section-title"><span class="tiny-dot" /> 内容分类</h2><button class="category-item" :class="{ selected: !query.categoryId }" @click="filter('categoryId')">全部记录 <small>{{ summary?.counts.contents ?? 0 }}</small></button><button v-for="category in categories" :key="category.id" class="category-item" :class="{ selected: String(query.categoryId) === String(category.id) }" @click="filter('categoryId', category.id)">{{ category.name }}<small>{{ category.count }}</small></button><p v-if="!categories.length" class="empty-note">内容会慢慢长出来。</p></section>
        <section class="glass panel"><h2 class="section-title"><span class="tiny-dot" /> 标签花园</h2><div class="tag-cloud"><button v-for="tag in tags" :key="tag.id" class="tag" :class="{ selected: String(query.tagId) === String(tag.id) }" @click="filter('tagId', tag.id)">{{ tag.name }}</button><span v-if="!tags.length" class="empty-note">等待第一颗种子。</span></div></section>
      </aside>
      <section class="feed-column"><div class="announcement glass"><span>◉</span><span>欢迎来到我的小小内容宇宙。</span><NuxtLink to="/search" aria-label="搜索内容">⌕ 搜索</NuxtLink></div><div class="filter-pills" aria-label="内容类型"><button :class="{ selected: !query.type }" @click="filter('type')">全部</button><button v-for="s in sections.filter(s => site.homeModules.includes(s.type))" :key="s.type" :class="{ selected: query.type === s.type }" @click="filter('type', s.type)">{{ s.label }}</button></div>
        <div v-if="filtered" class="filter-state">{{ route.query.date ? `${route.query.date} 的记录` : '正在按分类或标签筛选' }}<NuxtLink to="/">清除筛选 ×</NuxtLink></div>
        <div v-if="error" class="glass empty-state" role="alert"><h2>暂时没能读到这些记录</h2><p>请检查服务连接后重试。</p><button @click="refresh()">重新加载</button></div>
        <div v-else-if="status === 'pending' && !data" class="glass empty-state">正在翻开这一页…</div>
        <template v-else><ContentCard v-for="item in data?.items" :key="item.id" :item="item" /><div v-if="!data?.items.length" class="glass empty-state"><span class="empty-symbol">✧</span><h2>留一点空白，给下一篇记录。</h2><p>{{ filtered || query.type ? '这个筛选下还没有公开内容。' : '第一篇故事，正等待被写下。' }}</p><NuxtLink v-if="filtered || query.type" to="/">浏览全部记录 →</NuxtLink></div><Pagination :page="query.page" :size="6" :total="data?.total || 0" @change="filter('page', $event)" /></template>
        <p class="feed-note">写一点，想一点。让日子留下痕迹。</p>
      </section>
      <aside class="sidebar-stack right-sidebar"><section class="glass panel"><h2 class="section-title"><span class="tiny-dot" /> 最近发布</h2><div class="timeline"><NuxtLink v-for="item in recent?.items" :key="item.id" :to="contentUrl(item)"><time>{{ dateText(item.lastPublishedAt) }}</time><span>{{ item.title }}</span></NuxtLink><p v-if="!recent?.items.length" class="empty-note">新的故事，敬请期待。</p></div></section><section class="glass panel quote"><span class="quote-mark">“</span><p>风景在文字背后，<br>阅读始终在画面中央。</p><small>给自己一个安静思考的地方。</small></section><SiteCalendar :dates="summary?.calendar || []" :selected="String(route.query.date || '')" /></aside>
    </div>
  </div>
</template>
