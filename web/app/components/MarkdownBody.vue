<script setup lang="ts">
import { renderMarkdown } from '~/utils/markdown'
const props = withDefaults(defineProps<{ source?: string; preview?: boolean }>(), { source: '', preview: false })
const html = computed(() => renderMarkdown(props.source, props.preview).html)
const notice = ref('')
async function copy(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.matches('[data-copy-code]')) return
  try { await navigator.clipboard.writeText(target.closest('.code-block')?.querySelector('code')?.textContent || ''); notice.value = '代码已复制' }
  catch { notice.value = '复制失败，请手动选择代码' }
}
</script>
<template><div class="prose" v-html="html" @click="copy" /><p v-if="notice" role="status" class="empty-note">{{ notice }}</p></template>
