<script setup lang="ts">
import type { Content, Page, Taxon } from '~/types'
import { defaultSite, sections, contentUrl, dateText } from '~/utils/site'
const route = useRoute()
const { data: settings } = await useSite()
const site = computed(() => settings.value || defaultSite)
const { data: taxonomy } = await useFetch<{ tags: Taxon[] }>('/api/public/taxonomy')
const { data: summary } = await useFetch<{ counts: { contents: number; tags: number }; calendar: { date: string }[] }>('/api/public/summary')
const query = computed(() => ({ home: true, type: String(route.query.type || ''), tagId: route.query.tagId, date: route.query.date, page: Number(route.query.page || 1), size: 6 }))
const { data, status, error, refresh } = await useFetch<Page<Content>>('/api/public/contents', { query, key: 'home-contents' })
const { data: recent } = await useFetch<Page<Content>>('/api/public/contents', { query: { size: 3 }, key: 'recent-publications' })
const { data: latest } = await useFetch<Page<Content>>('/api/public/contents', { query: { size: 5 }, key: 'home-latest' })
const articlePath = sections.find(s => s.type === 'article')!.path
const showCarousel = computed(() => !route.query.type && !route.query.tagId && !route.query.date)
const tags = computed(() => taxonomy.value?.tags.filter(t => t.count).slice(0, 18) || [])
const filtered = computed(() => !!(route.query.tagId || route.query.date))
function filter(key: string, value?: string | number) { navigateTo({ path: '/', query: { ...route.query, [key]: value || undefined, ...(key === 'page' ? {} : { page: undefined }) } }) }
useSeoMeta({ title: '首页' })
</script>
<template>
  <div class="wrap">
    
    <section class="home-hero" :style="site.backgroundAssetId ? { backgroundImage: `url(/api/public/media/${site.backgroundAssetId})` } : {}">
      <div class="hero-inner">
        <p class="hero-kicker">{{ site.name }}</p>
        <h1>{{ site.headline || site.name }}</h1>
        <p class="hero-sub">{{ site.subtitle || site.description }}</p>
        <div class="hero-actions">
          <NuxtLink :to="`/${articlePath}`" class="hero-btn solid">开始阅读 →</NuxtLink>
          <a v-if="site.github" :href="site.github" target="_blank" rel="noopener noreferrer" class="hero-btn"><SiteIcon name="github" /> GitHub</a>
          <a v-if="site.email" :href="`mailto:${site.email}`" class="hero-btn"><SiteIcon name="mail" /> 写封邮件</a>
          <NuxtLink to="/about" class="hero-btn">关于我</NuxtLink>
        </div>
      </div>
    </section>
    <div class="home-grid">
      <aside class="sidebar-stack left-sidebar"><ProfileCard :site="site" />
        <section class="glass panel"><h2 class="section-title"><span class="tiny-dot" /> &#25353;&#26639;&#30446;&#27983;&#35272;</h2><button class="category-item" :class="{ selected: !query.type }" @click="filter('type')">&#20840;&#37096;&#35760;&#24405; <small>{{ summary?.counts.contents ?? 0 }}</small></button><button v-for="section in sections" :key="section.type" class="category-item" :class="{ selected: query.type === section.type }" @click="filter('type', section.type)">{{ section.label }}<small>&#27983;&#35272;</small></button></section>
        <section class="glass panel"><h2 class="section-title"><span class="tiny-dot" /> 标签花园</h2><div class="tag-cloud"><button v-for="tag in tags" :key="tag.id" class="tag" :class="{ selected: String(query.tagId) === String(tag.id) }" @click="filter('tagId', tag.id)">{{ tag.name }}</button><span v-if="!tags.length" class="empty-note">等待第一颗种子。</span></div></section>
      </aside>
      <section class="feed-column"><HomeCarousel v-if="showCarousel" :items="latest?.items || []" :site="site" /><div class="filter-pills" aria-label="内容类型"><button :class="{ selected: !query.type }" @click="filter('type')">全部</button><button v-for="s in sections.filter(s => site.homeModules.includes(s.type))" :key="s.type" :class="{ selected: query.type === s.type }" @click="filter('type', s.type)">{{ s.label }}</button></div>
        <div v-if="filtered" class="filter-state">{{ route.query.date ? `${route.query.date} 的记录` : '正在按标签筛选' }}<NuxtLink to="/">清除筛选 ×</NuxtLink></div>
        <div v-if="error" class="glass empty-state" role="alert"><h2>暂时没能读到这些记录</h2><p>请检查服务连接后重试。</p><button @click="refresh()">重新加载</button></div>
        <div v-else-if="status === 'pending' && !data" class="glass empty-state">正在翻开这一页…</div>
        <template v-else><ContentCard v-for="item in data?.items" :key="item.id" :item="item" /><div v-if="!data?.items.length" class="glass empty-state"><span class="empty-symbol">✧</span><h2>留一点空白，给下一篇记录。</h2><p>{{ filtered || query.type ? '这个筛选下还没有公开内容。' : '第一篇故事，正等待被写下。' }}</p><NuxtLink v-if="filtered || query.type" to="/">浏览全部记录 →</NuxtLink></div><Pagination :page="query.page" :size="6" :total="data?.total || 0" @change="filter('page', $event)" /></template>
        <p class="feed-note">写一点，想一点。让日子留下痕迹。</p>
      </section>
      <aside class="sidebar-stack right-sidebar"><section class="glass panel"><h2 class="section-title"><span class="tiny-dot" /> 最近发布</h2><div class="timeline"><NuxtLink v-for="item in recent?.items" :key="item.id" :to="contentUrl(item)"><time>{{ dateText(item.lastPublishedAt) }}</time><span>{{ item.title }}</span></NuxtLink><p v-if="!recent?.items.length" class="empty-note">新的故事，敬请期待。</p></div></section><section class="glass panel quote"><span class="quote-mark">“</span><p>风景在文字背后，<br>阅读始终在画面中央。</p><small>给自己一个安静思考的地方。</small></section><SiteCalendar :dates="summary?.calendar || []" :selected="String(route.query.date || '')" /></aside>
    </div>
  </div>
</template>
