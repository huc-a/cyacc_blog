<script setup lang="ts">const route=useRoute(); await navigateTo(`/articles/${encodeURIComponent(String(route.params.slug))}`, { redirectCode: 301 })</script>
<template><div /></template>
