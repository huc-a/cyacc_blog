<script setup lang="ts">
const open = defineModel<boolean>('open', { default: false })
function search(q: string) { navigateTo({ path: '/search', query: { q } }) }
</script>
<template><InlineSearch v-model:open="open" class="header-search" @search="search" /></template>
