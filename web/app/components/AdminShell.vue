<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { defaultSite, sections, themeStyle } from '~/utils/site'
const { data } = await useSite(); const site = computed(() => data.value || defaultSite)
const { account, logout } = useAuth(); const message = useMessage()
const open = ref(false); const route = useRoute()
watch(() => route.fullPath, () => { open.value = false })
const current = computed(() => {
  if (route.path.includes('/editor/')) return '内容编辑'
  const names: Record<string, string> = { dashboard: '工作台', analytics: '数据统计', media: '媒体库', taxonomy: '分类与标签', appearance: '外观设置', settings: '系统设置', logs: '操作日志' }
  const key = route.path.split('/')[2] || 'dashboard'
  return names[key] || sections.find(s => s.path === key)?.label || '管理后台'
})
async function signOut() { try { await logout() } catch (e) { message.error(errorText(e)) } }
</script>
<template><div class="admin-shell" :style="themeStyle(site)"><div class="landscape" aria-hidden="true" /><div class="admin-veil" /><button v-if="open" class="sidebar-overlay" @click="open = false" aria-label="关闭侧栏" /><aside class="admin-sidebar glass" :class="{ open }"><NuxtLink to="/admin/dashboard" class="brand"><span class="brand-mark">菜</span><span>{{ site.name }}<small>CONTENT STUDIO / 管理后台</small></span></NuxtLink><nav class="admin-nav" aria-label="管理导航"><NuxtLink to="/admin/dashboard"><span>◇</span>工作台</NuxtLink><NuxtLink to="/admin/analytics"><span>▥</span>数据统计</NuxtLink><div class="nav-group">内容 / CONTENT</div><NuxtLink v-for="s in sections" :key="s.type" :to="`/admin/${s.path}`"><span>{{ s.icon }}</span>{{ s.label }}管理</NuxtLink><NuxtLink to="/admin/taxonomy"><span>⌗</span>分类与标签</NuxtLink><div class="nav-group">站点 / MANAGE</div><NuxtLink to="/admin/media"><span>◫</span>媒体库</NuxtLink><NuxtLink to="/admin/appearance"><span>◌</span>外观设置</NuxtLink><NuxtLink to="/admin/settings"><span>⚙</span>系统设置</NuxtLink><NuxtLink to="/admin/logs"><span>≡</span>操作日志</NuxtLink></nav><div class="sidebar-account"><AdminAvatar /><div><strong>{{ account?.displayName || '管理员' }}</strong><small>把想法，慢慢写下来。</small></div><button @click="signOut" aria-label="退出登录" title="退出登录">↪</button></div></aside><div class="admin-main"><header class="admin-topbar"><div><button class="admin-menu" @click="open = !open" aria-label="打开管理导航">☰</button><span>管理后台 <i>/</i> {{ current }}</span></div><div class="admin-top-tools"><NuxtLink to="/admin/articles" class="admin-search-link">⌕ 搜索内容</NuxtLink><NuxtLink to="/" target="_blank">访问前台 ↗</NuxtLink><NuxtLink to="/admin/settings" aria-label="管理员资料"><AdminAvatar class="small" /></NuxtLink></div></header><main class="admin-content"><slot /></main><footer class="admin-footer">CAIYA CONTENT STUDIO / 认真记录，缓慢生长。</footer></div></div></template>
