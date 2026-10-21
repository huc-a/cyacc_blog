<script setup lang="ts">
import * as echarts from 'echarts/core'
import { LineChart, PieChart, HeatmapChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, PieChart, HeatmapChart, BarChart, GridComponent, TooltipComponent, LegendComponent, VisualMapComponent, CanvasRenderer])
const props = defineProps<{ option: Record<string, unknown>; height?: number }>()
const element = ref<HTMLDivElement | null>(null)
let chart: ReturnType<typeof echarts.init> | undefined, observer: ResizeObserver | undefined
onMounted(() => { chart = echarts.init(element.value!); chart.setOption(props.option); observer = new ResizeObserver(() => chart?.resize()); observer.observe(element.value!) })
watch(() => props.option, value => chart?.setOption(value, true), { deep: true })
onUnmounted(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="element" role="img" aria-label="访问统计图表" :style="{ height: `${height || 300}px`, width: '100%' }" /></template>
