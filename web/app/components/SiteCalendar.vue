<script setup lang="ts">
const props = defineProps<{ dates: { date: string }[]; selected?: string }>()
const now = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
const month = ref(now.slice(0, 7))
const first = computed(() => new Date(`${month.value}-01T00:00:00Z`))
const padding = computed(() => (first.value.getUTCDay() + 6) % 7)
const days = computed(() => new Date(Date.UTC(first.value.getUTCFullYear(), first.value.getUTCMonth() + 1, 0)).getUTCDate())
const published = computed(() => new Set(props.dates.map(d => String(d.date).slice(0, 10))))
function shift(offset: number) { const d = first.value; month.value = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + offset, 1)).toISOString().slice(0, 7) }
const dayKey = (day: number) => `${month.value}-${String(day).padStart(2, '0')}`
</script>
<template><section class="glass panel calendar-panel"><div class="panel-heading"><h2>日历</h2><span class="eyebrow">DAYS & NOTES</span></div><div class="calendar-head"><button @click="shift(-1)" aria-label="上个月">‹</button><span>{{ month.replace('-', ' 年 ') }} 月</span><button @click="shift(1)" aria-label="下个月">›</button></div><div class="calendar-grid"><small v-for="d in ['一', '二', '三', '四', '五', '六', '日']" :key="d">{{ d }}</small><i v-for="i in padding" :key="`blank-${i}`" /><template v-for="d in days" :key="d"><NuxtLink v-if="published.has(dayKey(d))" :to="{ path: '/', query: { date: dayKey(d) } }" :class="{ today: now === dayKey(d), chosen: selected === dayKey(d) }">{{ d }}</NuxtLink><span v-else :class="{ today: now === dayKey(d) }">{{ d }}</span></template></div><p class="calendar-note">有记录的日子，会留下小小的印记。</p></section></template>
