<script setup lang="ts">
import type { Content } from '~/types'
import { contentUrl } from '~/utils/site'
defineProps<{ navigation: NonNullable<Content['navigation']> }>()
</script>
<template>
  <nav class="content-navigation" aria-label="同栏目阅读导航">
    <NuxtLink v-if="navigation.previous" :to="contentUrl(navigation.previous)" rel="prev" class="content-neighbor previous">
      <span>← 上一篇</span><strong>{{ navigation.previous.title }}</strong>
    </NuxtLink>
    <div v-else class="content-neighbor unavailable"><span>← 上一篇</span><span>已是第一篇</span></div>
    <NuxtLink v-if="navigation.next" :to="contentUrl(navigation.next)" rel="next" class="content-neighbor next">
      <span>下一篇 →</span><strong>{{ navigation.next.title }}</strong>
    </NuxtLink>
    <div v-else class="content-neighbor next unavailable"><span>下一篇 →</span><span>已是最新一篇</span></div>
  </nav>
</template>
