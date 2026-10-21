<script setup lang="ts">
import { defaultSite, themeStyle, sections } from '~/utils/site'
const { data, error } = await useSite()
const site = computed(() => data.value || defaultSite)
const menu = ref(false)
const clear = useState('clear-reading', () => false)
const route = useRoute()
watch(() => route.fullPath, () => { menu.value = false })
useSeoMeta({ titleTemplate: title => title ? `${title} · ${site.value.name}` : site.value.name, description: () => site.value.description })
</script>
<template>
  <div class="site-shell" :class="{ 'clear-reading': clear }" :style="themeStyle(site)">
    <div class="landscape" aria-hidden="true" /><div class="veil" aria-hidden="true" />
    <a class="skip-link" href="#main">跳到正文</a>
    <header class="nav-shell glass">
      <NuxtLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">菜</span><span>{{ site.name }}<small>NOTES, CODE & EVERYDAY LIFE</small></span></NuxtLink>
      <nav class="desktop-nav" aria-label="主导航"><NuxtLink to="/">首页</NuxtLink><NuxtLink v-for="section in sections" :key="section.type" :to="`/${section.path}`">{{ section.label }}</NuxtLink></nav>
      <div class="nav-tools"><NuxtLink to="/search" class="search-link" aria-label="搜索">⌕ <span>搜索</span></NuxtLink><button class="quiet desktop-only" @click="clear = !clear" :aria-pressed="clear">{{ clear ? '山色阅读' : '清晰阅读' }}</button><button class="mobile-menu" :aria-expanded="menu" aria-label="打开导航" @click="menu = !menu">☰</button></div>
      <nav v-if="menu" class="mobile-nav" aria-label="移动导航"><NuxtLink to="/">首页</NuxtLink><NuxtLink v-for="section in sections" :key="section.type" :to="`/${section.path}`">{{ section.label }}</NuxtLink><NuxtLink to="/about">关于</NuxtLink></nav>
    </header>
    <div v-if="error" class="connection-error wrap" role="alert">站点服务暂时不可用，请稍后刷新。</div>
    <main id="main"><slot /></main>
    <footer class="site-footer wrap"><span>© {{ new Date().getFullYear() }} {{ site.name }} · {{ site.footer }}</span><div><NuxtLink to="/about">关于这里</NuxtLink><NuxtLink to="/admin">管理后台 ↗</NuxtLink></div></footer>
  </div>
</template>
