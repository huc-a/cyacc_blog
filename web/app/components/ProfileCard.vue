<script setup lang="ts">
import type { SiteSettings } from '~/types'
defineProps<{ site: SiteSettings; preview?: boolean }>()
</script>
<template>
  <section class="glass profile">
    <div class="profile-art"><img v-if="site.avatarAssetId" :src="preview ? `/api/admin/media/${site.avatarAssetId}/file` : `/api/public/media/${site.avatarAssetId}`" :alt="`${site.author}的头像`"><div v-else class="profile-art-placeholder"><span class="profile-orbit" /><span class="profile-initial">菜</span><small>A LITTLE WORLD OF MY OWN</small></div></div>
    <div class="profile-copy"><h2>{{ site.author }}</h2><p>{{ site.bio }}</p><div class="profile-bottom"><div class="profile-contacts"><a v-if="site.github" :href="site.github" target="_blank" rel="noopener noreferrer" title="GitHub" aria-label="GitHub"><SiteIcon name="github" /></a><a v-if="site.email" :href="`mailto:${site.email}`" :title="site.email" aria-label="邮箱"><SiteIcon name="mail" /></a></div><NuxtLink to="/about" class="profile-about">关于我 ↗</NuxtLink></div></div>
  </section>
</template>
