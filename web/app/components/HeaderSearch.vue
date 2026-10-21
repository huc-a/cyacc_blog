<script setup lang="ts">
const open = defineModel<boolean>('open', { default: false })
const root = ref<HTMLElement | null>(null), field = ref<HTMLInputElement | null>(null), query = ref('')
const id = useId()
async function show() { open.value = true; await nextTick(); field.value?.focus() }
function close() { open.value = false; root.value?.querySelector<HTMLButtonElement>('.search-trigger')?.focus() }
async function submit() { const q = query.value.trim(); if (!q) { field.value?.focus(); return }; open.value = false; await navigateTo({ path: '/search', query: { q } }) }
function outside(event: PointerEvent) { if (root.value && !root.value.contains(event.target as Node)) open.value = false }
onMounted(() => document.addEventListener('pointerdown', outside)); onUnmounted(() => document.removeEventListener('pointerdown', outside))
</script>
<template><div ref="root" class="header-search" @keydown.esc.prevent="close"><Transition name="search-reveal"><form v-if="open" :id="id" class="header-search-panel" role="search" aria-label="站内搜索" @submit.prevent="submit"><input ref="field" v-model="query" type="search" aria-label="搜索内容" placeholder="搜一搜，记录里的灵感…" maxlength="200"><button type="submit" class="quiet" aria-label="提交搜索"><SiteIcon name="search" /></button><button type="button" class="quiet" aria-label="关闭搜索" @click="close"><SiteIcon name="close" /></button></form></Transition><button class="search-trigger quiet" aria-label="搜索" :aria-expanded="open" :aria-controls="id" @click="open ? close() : show()"><SiteIcon name="search" /><span>搜索</span></button></div></template>
