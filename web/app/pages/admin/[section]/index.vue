<script setup lang="ts">
import type { Content, Page, Taxon, Category } from '~/types'
import { sections, dateText, statusText, contentUrl } from '~/utils/site'
import { useMessage, NSelect, NModal } from 'naive-ui'
definePageMeta({ layout: 'admin' })
const route = useRoute(), api = useApi(), message = useMessage(), confirm = useConfirm()
const section = computed(() => sections.find(s => s.path === route.params.section))
if (!section.value) throw createError({ statusCode: 404, message: '管理页面不存在' })
const listType = computed(() => section.value!.type === 'article' ? '' : section.value!.type)
const status = ref(String(route.query.status || 'all')), q = ref(''), search = ref(''), page = ref(1), tag = ref<number | null>(null), category = ref<number | null>(null), selected = ref<number[]>([]), busy = ref(false)
const query = computed(() => ({ type: listType.value, status: status.value, q: search.value, page: page.value, size: 10, tagId: tag.value || undefined, categoryId: category.value || undefined }))
const { data, error, refresh } = await useFetch<Page<Content>>('/api/admin/contents', { query, key: `admin-list-${route.params.section}` })
const { data: taxonomy } = await useFetch<{ tags: Taxon[]; categories: Category[] }>('/api/admin/taxonomy')
const options = computed(() => taxonomy.value?.tags.map(c => ({ label: c.name, value: c.id })) || [])
const categoryOptions = computed(() => taxonomy.value?.categories.map(c => ({ label: c.name, value: c.id })) || [])
watch([status, tag, category, search], () => { page.value = 1; selected.value = [] })
watch(page, () => { selected.value = [] })
useSeoMeta({ title: () => section.value?.type === 'article' ? '文章管理' : `${section.value?.label}管理` })
function createContent() { navigateTo(section.value!.type === 'article' ? '/admin/editor/new' : `/admin/editor/new?type=${section.value!.type}`) }
const importOpen = ref(false), importFiles = ref<File[]>([]), importing = ref(false), importResult = ref<{ created: number; items: { id: number; title: string; slug: string }[]; failed: { file: string; reason: string }[] } | null>(null)
function pickImportFiles(event: Event) { importFiles.value = Array.from((event.target as HTMLInputElement).files || []); importResult.value = null }
async function runImport() {
  if (!importFiles.value.length) return
  importing.value = true
  try {
    const form = new FormData()
    for (const file of importFiles.value) form.append('files', file)
    importResult.value = await api('/api/admin/contents/import', { method: 'POST', body: form })
    if (importResult.value!.created) { message.success(`已导入 ${importResult.value!.created} 篇草稿`); await refresh() }
  } catch (e) { message.error(errorText(e)) } finally { importing.value = false }
}
async function action(item: Content, action: string) {
  if (!await confirm(action === 'restore' ? '恢复这条内容？' : action === 'offline' ? '将内容下线？' : '移入回收站？', action === 'restore' ? '恢复后不会自动公开，需要再次发布。' : '前台将无法访问这条内容，历史版本会保留。')) return
  busy.value = true
  try { await api(`/api/admin/contents/${item.id}/${action}`, { method: 'POST', body: { lockVersion: item.lockVersion } }); message.success('操作已完成'); await refresh() } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
async function bulk(action: string) {
  if (!await confirm('批量操作', `确认${action === 'restore' ? '恢复' : '移入回收站'}选中的 ${selected.value.length} 条内容？`)) return
  busy.value = true; let completed = 0
  try { for (const id of selected.value) { const item = data.value!.items.find(i => i.id === id)!; await api(`/api/admin/contents/${id}/${action}`, { method: 'POST', body: { lockVersion: item.lockVersion } }); completed++ } message.success(`已完成 ${completed} 条`) } catch (e) { message.error(`已完成 ${completed} 条；${errorText(e)}`) } finally { selected.value = []; busy.value = false; await refresh() }
}
function selectAll(event: Event) { selected.value = (event.target as HTMLInputElement).checked ? data.value?.items.map(i => i.id) || [] : [] }
</script>
<template><div v-if="section"><AdminPageHead :title="section.type === 'article' ? '文章管理' : `${section.label}管理`" :subtitle="section.type === 'article' ? '写文章、改文章、导入文章，都在这里。' : `统一管理${section.label}内容。`"><button v-if="section.type === 'article'" class="button" @click="importOpen = true">导入 Markdown</button><button class="button primary" @click="createContent">＋ 新建{{ section.type === 'article' ? '文章' : section.label }}</button></AdminPageHead><section class="studio-panel table-panel"><div class="admin-tabs"><button v-for="s in [{ key: 'all', label: '全部' }, { key: 'draft', label: '草稿' }, { key: 'published', label: '已发布' }, { key: 'offline', label: '已下线' }, { key: 'trash', label: '回收站' }]" :key="s.key" :class="{ active: status === s.key }" @click="status = s.key">{{ s.label }}</button></div><form class="admin-filters" @submit.prevent="search = q"><input v-model="q" type="search" aria-label="搜索管理内容" placeholder="搜索标题、摘要或标签…" maxlength="200"><button>搜索</button><NSelect v-model:value="category" :options="categoryOptions" clearable placeholder="&#20840;&#37096;&#20998;&#31867;" aria-label="&#25353;&#20869;&#23481;&#20998;&#31867;&#31579;&#36873;" style="width:180px" /><NSelect v-model:value="tag" :options="options" clearable placeholder="全部标签" aria-label="筛选标签" style="width:180px" /><small>共 {{ data?.total || 0 }} 条</small></form><div v-if="error" class="admin-empty form-error" role="alert">内容加载失败。<button @click="refresh()">重新加载</button></div><div class="table-scroll"><table class="admin-table"><thead><tr><th><input type="checkbox" aria-label="选择当前页" :checked="!!data?.items.length && selected.length === data.items.length" @change="selectAll"></th><th>标题</th><th>类型</th><th>标签</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in data?.items" :key="item.id"><td><input v-model="selected" type="checkbox" :value="item.id" :aria-label="`选择${item.title}`"></td><td class="title-cell"><NuxtLink v-if="status !== 'trash'" :to="`/admin/editor/${item.id}`">{{ item.title || '未命名草稿' }}</NuxtLink><span v-else>{{ item.title || '未命名草稿' }}</span><small>/{{ item.slug }}</small></td><td>{{ sections.find(s => s.type === item.contentType)?.label || item.contentType }}</td><td>{{ item.tagNames?.map(tag => `#${tag}`).join(' ') || '未标签' }}</td><td><span class="state-badge" :class="item.publishStatus">{{ statusText(item.publishStatus) }}</span><small v-if="item.revisionState === 'editing' && item.publishStatus === 'published'" class="pending-draft">有未发布修改</small></td><td>{{ dateText(item.updatedAt) }}</td><td><div class="row-actions"><template v-if="status === 'trash'"><button :disabled="busy" @click="action(item, 'restore')">恢复</button></template><template v-else><NuxtLink :to="`/admin/editor/${item.id}`">编辑</NuxtLink><NuxtLink v-if="item.publishStatus === 'published'" :to="contentUrl(item)" target="_blank">查看</NuxtLink><button v-if="item.publishStatus === 'published'" :disabled="busy" @click="action(item, 'offline')">下线</button><button class="danger" :disabled="busy" @click="action(item, 'trash')">回收</button></template></div></td></tr><tr v-if="!error && !data?.items.length"><td :colspan="7" class="admin-empty">没有匹配的内容，试试调整筛选条件。</td></tr></tbody></table></div><div v-if="selected.length" class="bulk-actions"><span>已选择 {{ selected.length }} 条</span><button :disabled="busy" @click="bulk(status === 'trash' ? 'restore' : 'trash')">{{ status === 'trash' ? '批量恢复' : '移入回收站' }}</button></div><Pagination :page="page" :total="data?.total || 0" :size="10" @change="page = $event" /></section><p class="admin-footnote">草稿会独立保存。只有发布后，修改才会出现在前台。</p><NModal v-model:show="importOpen" preset="card" title="导入 Markdown 文章" style="max-width:520px"><p class="admin-footnote" style="margin-top:0">选择一个或多个 .md 文件，导入后成为草稿文章。支持文件开头的 front-matter（title、tags）；没有标题时依次取正文一级标题、文件名。访问地址自动生成数字编号。</p><label class="import-drop"><input type="file" accept=".md,.markdown" multiple hidden @change="pickImportFiles">{{ importFiles.length ? `已选择 ${importFiles.length} 个文件` : '点击选择 .md 文件（可多选）' }}</label><div v-if="importFiles.length" class="import-result"><div v-for="f in importFiles" :key="f.name">{{ f.name }}<small style="color:#97a487"> · {{ Math.max(1, Math.round(f.size / 1024)) }} KB</small></div></div><div v-if="importResult" class="import-result"><p>成功导入 {{ importResult.created }} 篇草稿。</p><p v-for="f in importResult.failed" :key="f.file" class="fail">{{ f.file }}：{{ f.reason }}</p></div><template #footer><div class="admin-actions" style="justify-content:flex-end"><button @click="importOpen = false">关闭</button><button class="primary" :disabled="!importFiles.length || importing" @click="runImport">{{ importing ? '正在导入…' : '开始导入' }}</button></div></template></NModal></div></template>
