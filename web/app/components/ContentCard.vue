<script setup lang="ts">
import type { Content } from '~/types'
import { contentUrl, sectionOf, dateText } from '~/utils/site'
const props = defineProps<{ item: Content; compact?: boolean }>()
const { data: siteData } = await useSite()
const coverId = computed(() => props.item.coverAssetId || siteData.value?.defaultCoverAssetId || null)
</script>
<template>
  <article class="content-card glass" :class="{ compact, 'has-cover': coverId }">
    <NuxtLink :to="contentUrl(item)" class="card-link" :aria-label="item.title">
      <div class="card-visual">
        <img v-if="coverId" class="card-cover" :src="`/api/public/media/${coverId}?variant=display`" alt="" loading="lazy">
        <div class="card-inner"><h2>{{ item.title }}</h2><p>{{ item.summary }}</p></div>
      </div>
      <div class="card-meta"><time :datetime="item.lastPublishedAt">{{ dateText(item.lastPublishedAt) }}</time><span class="badge">{{ item.categoryName || sectionOf(item.contentType).label }}</span><span v-for="tag in item.tagNames || item.tags?.map(tag => tag.name)" :key="tag" class="card-tag">#{{ tag }}</span><span class="read-link" aria-hidden="true">↗</span></div>
    </NuxtLink>
  </article>
</template>
