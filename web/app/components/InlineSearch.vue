<script setup lang="ts">
const props = withDefaults(defineProps<{ fieldLabel?: string; searchLabel?: string; allowEmpty?: boolean; collapseOnSubmit?: boolean }>(), {
  fieldLabel: '搜索内容', searchLabel: '站内搜索', allowEmpty: false, collapseOnSubmit: true
})
const query = defineModel<string>({ default: '' }), open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{ search: [query: string] }>()
const root = ref<HTMLFormElement | null>(null), field = ref<HTMLInputElement | null>(null), trigger = ref<HTMLButtonElement | null>(null)
const id = useId()
async function show(event: MouseEvent) { if (open.value) return; event.preventDefault(); open.value = true; await nextTick(); field.value?.focus() }
function close() { open.value = false; trigger.value?.focus() }
function submit() {
  const value = query.value.trim()
  if (!value && !props.allowEmpty) { field.value?.focus(); return }
  query.value = value; emit('search', value)
  if (props.collapseOnSubmit) open.value = false
}
function outside(event: PointerEvent) { if (root.value && !root.value.contains(event.target as Node)) open.value = false }
onMounted(() => document.addEventListener('pointerdown', outside))
onUnmounted(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <form ref="root" class="inline-disclosure-search" :class="{ 'is-open': open }" role="search" :aria-label="searchLabel" @submit.prevent="submit" @keydown.esc.prevent="close">
    <Transition name="inline-search-reveal"><div v-if="open" :id="id" class="search-field"><input ref="field" v-model="query" type="search" :aria-label="fieldLabel" placeholder="输入关键词…" maxlength="200"><button type="button" class="quiet search-close" aria-label="关闭搜索" @click="close"><SiteIcon name="close" /></button></div></Transition>
    <button ref="trigger" :type="open ? 'submit' : 'button'" class="search-trigger quiet" :aria-label="open ? '提交搜索' : '搜索'" :aria-expanded="open" :aria-controls="id" @click="show"><SiteIcon name="search" /><span v-if="!open">搜索</span></button>
  </form>
</template>
