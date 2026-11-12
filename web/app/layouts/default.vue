<script setup lang="ts">
import { defaultSite, themeStyle, sections } from '~/utils/site'
const article = sections.find(section => section.type === 'article')!
const { data, error } = await useSite()
const site = computed(() => data.value || defaultSite)
const menu = ref(false)
const searching = ref(false), scrolled = ref(false)
watch(searching, open => { if (open) menu.value = false })
function onScroll() { scrolled.value = window.scrollY > 36 }
onMounted(() => { onScroll(); window.addEventListener('scroll', onScroll, { passive: true }) })
onUnmounted(() => window.removeEventListener('scroll', onScroll))
const route = useRoute()
watch(() => route.fullPath, () => { menu.value = false; searching.value = false })
useSeoMeta({ titleTemplate: title => title ? `${title} · ${site.value.name}` : site.value.name, description: () => site.value.description })
</script>
<template>
  <div class="site-shell" :style="themeStyle(site)">
    <div class="landscape" aria-hidden="true" /><div class="veil" aria-hidden="true" />
    <a class="skip-link" href="#main">跳到正文</a>
    <header class="nav-shell glass" :class="{ scrolled, searching, 'menu-open': menu }">
      <NuxtLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">菜</span><span>{{ site.name }}<small>NOTES, CODE & EVERYDAY LIFE</small></span></NuxtLink>
      <nav class="desktop-nav" aria-label="主导航"><NuxtLink to="/"><SiteIcon name="home" />首页</NuxtLink><NuxtLink :to="`/${article.path}`"><SiteIcon name="article" />{{ article.label }}</NuxtLink></nav>
      <div class="nav-tools"><HeaderSearch v-model:open="searching" /><button class="mobile-menu" :aria-expanded="menu" aria-label="打开导航" @click="menu = !menu; searching = false">☰</button></div>
      <nav v-if="menu" class="mobile-nav" aria-label="移动导航"><NuxtLink to="/"><SiteIcon name="home" />首页</NuxtLink><NuxtLink :to="`/${article.path}`"><SiteIcon name="article" />{{ article.label }}</NuxtLink></nav>
    </header>
    <div v-if="error" class="connection-error wrap" role="alert">站点服务暂时不可用，请稍后刷新。</div>
    <main id="main"><slot /></main>
    <footer class="site-footer wrap"><div class="footer-identity"><span>© {{ new Date().getFullYear() }} {{ site.name }} · {{ site.footer }}</span><a v-if="site.icpNumber" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" class="icp-record">{{ site.icpNumber }}</a><a v-if="site.policeNumber" :href="`https://beian.mps.gov.cn/#/query/webSearch?code=${site.policeNumber.replace(/\D/g, '')}`" target="_blank" rel="noopener noreferrer" class="police-record">{{ site.policeNumber }}</a><span v-else class="police-record pending">公安备案办理中</span></div><NuxtLink to="/about" class="footer-about">关于这里</NuxtLink></footer>
  </div>
</template>
