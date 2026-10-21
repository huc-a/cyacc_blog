<script setup lang="ts">
import type { Content } from '~/types'
import { contentUrl, sectionOf, dateText, statusText, defaultSite } from '~/utils/site'
import { renderMarkdown } from '~/utils/markdown'
const props = defineProps<{ item: Content; chapterSlug?: string }>()
const { data: site } = await useSite()
const chapterIndex = computed(() => props.item.details?.chapters?.findIndex(c => c.slug === props.chapterSlug) ?? -1)
const chapter = computed(() => props.item.details?.chapters?.[chapterIndex.value])
if (props.chapterSlug && !chapter.value) throw createError({ statusCode: 404, message: '章节不存在' })
const title = computed(() => chapter.value?.title || props.item.title)
const source = computed(() => chapter.value?.bodyMarkdown || props.item.bodyMarkdown || '')
const rendered = computed(() => renderMarkdown(source.value))
const reading = useCookie<{ size: number; sans: boolean; focus: boolean }>('reading-preferences', { default: () => ({ size: 0, sans: false, focus: false }), maxAge: 31536000, sameSite: 'lax' })
const fontSize = computed(() => reading.value.size || site.value?.fontSize || 19)
const titleSection = computed(() => sectionOf(props.item.contentType))
const duration = computed(() => Math.max(1, Math.ceil(source.value.length / 500)))
const notice = ref('')
async function copy(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (target.matches('[data-copy-code]')) {
    const text = target.closest('.code-block')?.querySelector('code')?.textContent || ''
    try { await navigator.clipboard.writeText(text); notice.value = '代码已复制' } catch { notice.value = '复制失败，请手动选择代码' }
  }
}
useSeoMeta({ title: () => title.value, description: () => props.item.summary, ogTitle: () => title.value, ogType: 'article' })
const config = useRuntimeConfig(); const route = useRoute()
useHead({ link: [{ rel: 'canonical', href: new URL(route.path, config.public.siteUrl).toString() }] })
</script>
<template><div class="wrap reading" :class="{ focused: reading.focus }"><div class="breadcrumb"><NuxtLink to="/">首页</NuxtLink><span>/</span><NuxtLink :to="`/${titleSection.path}`">{{ titleSection.label }}</NuxtLink><template v-if="chapter"><span>/</span><NuxtLink :to="contentUrl(item)">{{ item.title }}</NuxtLink></template></div><div class="read-grid"><article class="reading-paper glass" :style="{ '--reading-size': `${fontSize}px`, '--reading-font': reading.sans ? 'var(--ui-font)' : 'var(--serif-font)' }"><header class="article-header"><span class="eyebrow">{{ titleSection.eyebrow }}</span><h1>{{ title }}</h1><div class="article-meta"><span>{{ (site || defaultSite).author }}</span><time>{{ dateText(item.lastPublishedAt) }}</time><span>{{ duration }} 分钟阅读</span><span v-if="item.category">{{ item.category.name }}</span></div><p class="article-summary">{{ item.summary }}</p><div class="reading-tools"><span>阅读设置</span><button aria-label="减小字号" :disabled="fontSize <= 16" @click="reading.size = Math.max(16, fontSize - 1)">A−</button><button aria-label="增大字号" :disabled="fontSize >= 25" @click="reading.size = Math.min(25, fontSize + 1)">A＋</button><button :aria-pressed="reading.focus" @click="reading.focus = !reading.focus">{{ reading.focus ? '退出专注' : '专注模式' }}</button><button :aria-pressed="reading.sans" @click="reading.sans = !reading.sans">{{ reading.sans ? '切换衬线' : '切换无衬线' }}</button></div></header>
      <img v-if="item.coverAssetId && !chapter" class="reader-cover" :src="`/api/public/media/${item.coverAssetId}`" :alt="item.title">
      <div v-if="item.contentType === 'deal'" class="detail-info"><span class="badge" :class="item.details?.validity">{{ statusText(item.details?.validity || 'unknown') }}</span><strong>{{ item.details?.benefitText }}</strong><p v-if="item.details?.startsAt || item.details?.endsAt">有效期：{{ item.details?.startsAt ? dateText(item.details.startsAt) : '未限定开始日期' }} — {{ item.details?.endsAt ? dateText(item.details.endsAt) : '未限定截止日期' }}</p><p v-if="item.details?.verification">核验于 {{ dateText(item.details.verification.verifiedAt) }}</p></div>
      <div class="prose" v-html="rendered.html" @click="copy" />
      <div v-if="item.details?.techStack?.length" class="tag-cloud tech-stack"><span v-for="tech in item.details.techStack" :key="tech" class="tag">{{ tech }}</span></div>
      <section v-if="item.details?.prerequisitesMarkdown && !chapter" class="detail-info"><h2>开始之前</h2><MarkdownBody :source="item.details.prerequisitesMarkdown" /></section>
      <section v-if="item.details?.requirementsMarkdown" class="detail-info"><h2>适用条件与限制</h2><MarkdownBody :source="item.details.requirementsMarkdown" /></section>
      <div v-if="item.galleryAssetIds?.length" class="gallery"><img v-for="id in item.galleryAssetIds" :key="id" :src="`/api/public/media/${id}`" alt="作品截图" loading="lazy"></div>
      <div class="external-links"><a v-if="item.details?.demoUrl" :href="item.details.demoUrl" target="_blank" rel="noopener noreferrer">查看演示 ↗</a><a v-if="item.details?.websiteUrl" :href="item.details.websiteUrl" target="_blank" rel="noopener noreferrer">访问网站 ↗</a><a v-if="item.details?.sourceUrl" :href="item.details.sourceUrl" target="_blank" rel="noopener noreferrer">{{ item.contentType === 'work' ? '查看源码' : '信息来源' }} ↗</a><a v-if="item.details?.claimUrl" :href="item.details.claimUrl" target="_blank" rel="noopener noreferrer">领取入口 ↗</a></div>
      <section v-if="item.details?.chapters?.length" class="chapter-list"><h2>{{ chapter ? '系列章节' : '从这里开始阅读' }}</h2><NuxtLink v-for="(c, index) in item.details.chapters" :key="c.chapterKey" :to="`${contentUrl(item)}/${c.slug}`" :class="{ current: c.slug === chapterSlug }"><span>{{ String(index + 1).padStart(2, '0') }}</span>{{ c.title }}<span>→</span></NuxtLink></section>
      <div v-if="chapter" class="chapter-navigation"><NuxtLink v-if="chapterIndex > 0" :to="`${contentUrl(item)}/${item.details!.chapters![chapterIndex - 1]!.slug}`">← 上一章</NuxtLink><NuxtLink v-if="chapterIndex < item.details!.chapters!.length - 1" :to="`${contentUrl(item)}/${item.details!.chapters![chapterIndex + 1]!.slug}`">下一章 →</NuxtLink></div>
      <footer class="article-end"><span class="eyebrow">THE END / 谢谢阅读</span><p>把想法留在这里，也让它们继续生长。</p><div class="tag-cloud"><NuxtLink v-for="tag in item.tags" :key="tag.id" :to="{ path: '/', query: { tagId: tag.id } }" class="tag"># {{ tag.name }}</NuxtLink></div><NuxtLink :to="`/${titleSection.path}`">← 返回{{ titleSection.label }}</NuxtLink></footer><p v-if="notice" role="status">{{ notice }}</p>
    </article><aside class="reader-sidebar"><section class="glass panel"><h2 class="section-title">本文目录 / CONTENTS</h2><nav class="toc"><a v-for="h in rendered.headings" :key="h.id" :href="`#${h.id}`" :class="{ nested: h.level > 2 }">{{ h.text }}</a><p v-if="!rendered.headings.length" class="empty-note">慢慢读，不必着急。</p></nav></section><section class="glass panel quote"><p>背景可以通透，<br>文字需要安稳。</p><small>在一页文字里，停留片刻。</small></section></aside></div></div></template>
