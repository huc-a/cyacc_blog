<script setup lang="ts">
import type { Content } from '~/types'
import { contentUrl, sectionOf, dateText } from '~/utils/site'
defineProps<{ item: Content; compact?: boolean }>()
</script>
<template>
  <article class="content-card glass" :class="{ compact }">
    <NuxtLink v-if="item.coverAssetId" :to="contentUrl(item)" class="cover"><img :src="`/api/public/media/${item.coverAssetId}`" :alt="item.title" loading="lazy"></NuxtLink>
    <div class="card-inner"><div class="badges"><span class="badge">{{ sectionOf(item.contentType).label }}</span><span class="muted">{{ item.categoryName || item.category?.name || '日常记录' }}</span></div>
      <h2><NuxtLink :to="contentUrl(item)">{{ item.title }}</NuxtLink></h2><p>{{ item.summary }}</p>
      <div class="card-meta"><time>{{ dateText(item.lastPublishedAt) }}</time><span>第 {{ item.revisionNo }} 版</span><NuxtLink :to="contentUrl(item)" class="read-link">{{ item.contentType === 'tutorial' ? '开始阅读' : '继续阅读' }} <span>↗</span></NuxtLink></div>
    </div>
  </article>
</template>
