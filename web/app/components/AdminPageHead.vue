<script setup lang="ts">defineProps<{ title: string; subtitle?: string }>()</script>
<template><header class="admin-page-head"><div><span class="eyebrow">CAIYA / CONTENT STUDIO</span><h1>{{ title }}</h1><p v-if="subtitle">{{ subtitle }}</p></div><div class="admin-actions"><slot /></div></header></template>
