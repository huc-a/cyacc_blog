<script setup lang="ts">
import type { Content } from '~/types'
import { sectionOf, dateText, defaultSite } from '~/utils/site'
import { renderMarkdown } from '~/utils/markdown'
const props = defineProps<{ item: Content }>()
const { data: site } = await useSite()
const title = computed(() => props.item.title)
const source = computed(() => props.item.bodyMarkdown || '')
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
<template><div class="wrap reading" :class="{ focused: reading.focus }"><div class="breadcrumb"><NuxtLink to="/">首页</NuxtLink><span>/</span><NuxtLink :to="`/${titleSection.path}`">{{ titleSection.label }}</NuxtLink></div><div class="read-grid"><article class="reading-paper glass" :style="{ '--reading-size': `${fontSize}px`, '--reading-font': reading.sans ? 'var(--ui-font)' : 'var(--serif-font)' }"><header class="article-header"><span class="eyebrow">{{ titleSection.eyebrow }}</span><h1>{{ title }}</h1><div class="article-meta"><span>{{ (site || defaultSite).author }}</span><time>{{ dateText(item.lastPublishedAt) }}</time><span>{{ duration }} 分钟阅读</span></div><p class="article-summary">{{ item.summary }}</p><div class="reading-tools"><span>阅读设置</span><button aria-label="减小字号" :disabled="fontSize <= 16" @click="reading.size = Math.max(16, fontSize - 1)">A−</button><button aria-label="增大字号" :disabled="fontSize >= 25" @click="reading.size = Math.min(25, fontSize + 1)">A＋</button><button :aria-pressed="reading.focus" @click="reading.focus = !reading.focus">{{ reading.focus ? '退出专注' : '专注模式' }}</button><button :aria-pressed="reading.sans" @click="reading.sans = !reading.sans">{{ reading.sans ? '切换衬线' : '切换无衬线' }}</button></div></header>
      <img v-if="item.coverAssetId" class="reader-cover" :src="`/api/public/media/${item.coverAssetId}`" :alt="item.title">
      <div class="prose" v-html="rendered.html" @click="copy" />
      <div v-if="item.galleryAssetIds?.length" class="gallery"><img v-for="id in item.galleryAssetIds" :key="id" :src="`/api/public/media/${id}`" alt="文章配图" loading="lazy"></div>
      <footer class="article-end"><span class="eyebrow">THE END / 谢谢阅读</span><p>把想法留在这里，也让它们继续生长。</p><div class="tag-cloud"><NuxtLink v-for="tag in item.tags" :key="tag.id" :to="{ path: '/', query: { tagId: tag.id } }" class="tag"># {{ tag.name }}</NuxtLink></div><ContentNavigation v-if="item.navigation" :navigation="item.navigation" /><NuxtLink :to="`/${titleSection.path}`">← 返回{{ titleSection.label }}</NuxtLink></footer><p v-if="notice" role="status">{{ notice }}</p>
    </article><aside class="reader-sidebar"><section class="glass panel"><h2 class="section-title">本文目录 / CONTENTS</h2><nav class="toc"><a v-for="h in rendered.headings" :key="h.id" :href="`#${h.id}`" :class="{ nested: h.level > 2 }">{{ h.text }}</a><p v-if="!rendered.headings.length" class="empty-note">慢慢读，不必着急。</p></nav></section><section class="glass panel quote"><p>背景可以通透，<br>文字需要安稳。</p><small>在一页文字里，停留片刻。</small></section></aside></div></div></template>
