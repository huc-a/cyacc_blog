<script setup lang="ts">
import { defaultSite } from '~/utils/site'
const { data } = await useSite(); const site = computed(() => data.value || defaultSite)
useSeoMeta({ title: '关于这里' })
</script>
<template><article class="about-page reading-paper glass wrap"><span class="eyebrow">ABOUT / 关于这里</span><img v-if="site.avatarAssetId" class="about-avatar" :src="`/api/public/media/${site.avatarAssetId}`" :alt="`${site.author}的头像`"><div v-else class="avatar">菜</div><h1>你好，我是{{ site.author }}。</h1><p class="prose">{{ site.bio }}</p><p class="prose">{{ site.description }}</p><div class="external-links"><a v-if="site.github" :href="site.github" target="_blank" rel="noopener noreferrer">GitHub ↗</a><a v-if="site.email" :href="`mailto:${site.email}`">写一封邮件 ↗</a></div><div class="article-end"><span class="eyebrow">A SMALL SPACE, A LONG JOURNEY</span><p>记录、分享，让思考在这里慢慢生长。</p><NuxtLink to="/">读一篇记录 →</NuxtLink></div></article></template>
