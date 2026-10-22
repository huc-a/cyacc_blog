<script setup lang="ts">
import type { Content } from '~/types'
import { contentUrl, sectionOf, dateText } from '~/utils/site'
defineProps<{ item: Content; compact?: boolean }>()
</script>
<template>
  <article class="content-card glass" :class="{ compact, 'has-cover': item.coverAssetId }">
    <NuxtLink :to="contentUrl(item)" class="card-link" :aria-label="item.title">
      <div class="card-visual">
        <img v-if="item.coverAssetId" class="card-cover" :src="`/api/public/media/${item.coverAssetId}`" alt="" loading="lazy">
        <div class="card-inner"><h2>{{ item.title }}</h2><p>{{ item.summary }}</p></div>
      </div>
      <div class="card-meta"><time :datetime="item.lastPublishedAt">{{ dateText(item.lastPublishedAt) }}</time><span class="badge">{{ sectionOf(item.contentType).label }}</span><span v-if="item.categoryName || item.category?.name" class="card-category">{{ item.categoryName || item.category?.name }}</span><span v-for="tag in item.tagNames || item.tags?.map(tag => tag.name)" :key="tag" class="card-tag">#{{ tag }}</span><span class="read-link" aria-hidden="true">↗</span></div>
    </NuxtLink>
  </article>
</template>
