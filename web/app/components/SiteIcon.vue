<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string[]> = {
  home: ['m3 10 9-7 9 7', 'M5 9v12h5v-7h4v7h5V9'],
  article: ['M6 3h9l4 4v14H6z', 'M14 3v5h5', 'M9 12h7M9 16h5'],
  work: ['M4 7h16v14H4z', 'M9 7V3h6v4', 'M4 12h16M10 12v3h4v-3'],
  tutorial: ['M12 5c-3-2-7-2-10 0v15c3-2 7-2 10 0 3-2 7-2 10 0V5c-3-2-7-2-10 0Z', 'M12 5v15'],
  deal: ['M3 10h18v11H3zM2 6h20v4H2zM12 6v15', 'M12 6H7C1-1 13-1 12 6Zm0 0h5c6-7-6-7-5 0Z'],
  profile: ['M20 21a8 8 0 0 0-16 0', 'M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z'],
  search: ['M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13', 'm16 16 5 5'],
  close: ['m6 6 12 12M6 18 18 6'],
  mail: ['M3 5h18v14H3z', 'm3 5 9 8 9-8'],
  github: ['M9 21v-4c-4 1-4-2-6-2M15 21v-4c0-1-1-2-1-2 5 0 6-3 6-5 0-2-1-3-1-3 0-2 0-3-1-4-2 0-4 2-4 2a11 11 0 0 0-4 0S8 3 6 3C5 4 5 5 6 7c0 0-2 1-2 3 0 3 2 5 6 5-1 0-1 1-1 2'],
}
</script>
<template><svg class="site-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path v-for="(d, i) in paths[name] || paths.article" :key="i" :d="d" /></svg></template>
