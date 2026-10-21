<script setup lang="ts">
const props = defineProps<{ page: number; total: number; size: number }>()
const emit = defineEmits<{ change: [page: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
<template><nav v-if="total > size" class="pagination" aria-label="分页"><button :disabled="page <= 1" aria-label="上一页" @click="emit('change', page - 1)">←</button><span>第 {{ page }} / {{ pages }} 页</span><button :disabled="page >= pages" aria-label="下一页" @click="emit('change', page + 1)">→</button><small>共 {{ total }} 条记录</small></nav></template>
