<script setup lang="ts">
import type { Page } from '~/types'
import { dateText } from '~/utils/site'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '操作日志' })
type Log = { id: number; action: string; targetType: string; targetId: number; result: string; createdAt: string; displayName?: string }
const page = ref(1)
const { data, error, refresh } = await useFetch<Page<Log>>('/api/admin/logs', { query: computed(() => ({ page: page.value, size: 20 })) })
const labels: Record<string, string> = { 'content.create': '新建内容', 'content.save': '保存草稿', 'content.publish': '发布内容', 'content.offline': '内容下线', 'content.trash': '移入回收站', 'content.restore': '恢复内容', 'media.upload': '上传媒体', 'media.delete': '删除媒体', 'site.update': '更新网站设置', 'account.password': '修改管理员密码', 'account.profile': '修改管理员资料', 'taxonomy.create': '新建标签', 'taxonomy.update': '修改标签', 'taxonomy.delete': '删除标签', 'deal.verify': '人工核验羊毛' }
</script>
<template><div><AdminPageHead title="操作日志" subtitle="每一次整理与发布，都留下清晰的记录。"><button @click="refresh()">刷新</button></AdminPageHead><section class="studio-panel"><p v-if="error" class="form-error" role="alert">日志加载失败，请重试。</p><div class="table-scroll"><table class="admin-table"><thead><tr><th>时间</th><th>操作</th><th>管理员</th><th>对象</th><th>结果</th></tr></thead><tbody><tr v-for="log in data?.items" :key="log.id"><td>{{ dateText(log.createdAt) }} {{ new Date(log.createdAt).toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</td><td>{{ labels[log.action] || log.action }}</td><td>{{ log.displayName || '系统' }}</td><td>{{ log.targetType }} #{{ log.targetId }}</td><td><span class="state-badge published">{{ log.result === 'success' ? '成功' : log.result }}</span></td></tr></tbody></table></div><Pagination :page="page" :total="data?.total || 0" :size="20" @change="page = $event" /></section></div></template>
