<script setup lang="ts">
import type { Content, SiteSettings } from '~/types'
import { contentUrl, dateText, sectionOf } from '~/utils/site'
const props = defineProps<{ items: Content[]; site: SiteSettings }>()
const index = ref(0)
const paused = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
const count = computed(() => props.items.length)
function go(next: number) { index.value = ((next % count.value) + count.value) % count.value }
function coverOf(item: Content) { return item.coverAssetId || props.site.defaultCoverAssetId || null }
onMounted(() => {
  timer = setInterval(() => { if (!paused.value && count.value > 1) go(index.value + 1) }, 5500)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <section v-if="count" class="home-carousel glass" aria-label="最新文章轮播" @mouseenter="paused = true" @mouseleave="paused = false" @focusin="paused = true" @focusout="paused = false">
    <article v-for="(item, i) in items" :key="item.id" class="carousel-slide" :class="{ active: i === index }" :aria-hidden="i !== index">
      <img v-if="coverOf(item)" class="carousel-cover" :src="`/api/public/media/${coverOf(item)}?variant=display`" alt="" loading="lazy">
      <div class="carousel-shade" />
      <div class="carousel-copy">
        <span class="carousel-badge">最新 · {{ sectionOf(item.contentType).label }}</span>
        <h2><NuxtLink :to="contentUrl(item)">{{ item.title }}</NuxtLink></h2>
        <p>{{ item.summary }}</p>
        <time>{{ dateText(item.lastPublishedAt) }}</time>
      </div>
    </article>
    <div v-if="count > 1" class="carousel-controls">
      <button aria-label="上一篇文章" @click="go(index - 1)">‹</button>
      <div class="carousel-dots">
        <button v-for="(item, i) in items" :key="item.id" :class="{ active: i === index }" :aria-label="`切换到第 ${i + 1} 篇`" @click="go(i)" />
      </div>
      <button aria-label="下一篇文章" @click="go(index + 1)">›</button>
    </div>
  </section>
</template>
