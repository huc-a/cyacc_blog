<script setup lang="ts">
import type { Taxon, Category, Page } from '~/types'
import { NModal, useMessage } from 'naive-ui'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '分类与标签' })
const api = useApi(), message = useMessage(), confirm = useConfirm()
const active = ref<'categories' | 'tags'>('categories')
const page = ref(1), size = 10, q = ref(''), search = ref('')
const query = computed(() => ({ page: page.value, size, q: search.value }))
const { data, error, refresh } = await useFetch<Page<Taxon | Category>>(() => `/api/admin/taxonomy/${active.value}/paged`, { query, watch: [active] })
const show = ref(false), busy = ref(false), editing = ref<Taxon | Category | null>(null)
const form = reactive({ name: '', sortOrder: 0 })
const modalTitle = computed(() => `${editing.value ? '编辑' : '新建'}${active.value === 'categories' ? '分类' : '标签'}`)
const emptyText = computed(() => active.value === 'categories' ? '还没有文章分类。' : '还没有标签。')
const newLabel = computed(() => active.value === 'categories' ? '分类' : '标签')
watch(active, () => { page.value = 1 })
watch(search, () => { page.value = 1 })
function open(item?: Taxon | Category) {
  editing.value = item || null
  Object.assign(form, { name: item?.name || '', sortOrder: (item as Category | undefined)?.sortOrder || 0 })
  show.value = true
}
async function save() {
  busy.value = true
  try {
    const endpoint = active.value === 'categories' ? '/api/admin/taxonomy/categories' : '/api/admin/taxonomy/tags'
    const body = active.value === 'categories' ? { name: form.name, sortOrder: form.sortOrder } : { name: form.name }
    await api(`${endpoint}${editing.value ? `/${editing.value.id}` : ''}`, { method: editing.value ? 'PUT' : 'POST', body })
    show.value = false; message.success('已保存'); await refresh()
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
async function remove(item: Taxon | Category) {
  if (!await confirm(active.value === 'categories' ? '删除分类？' : '删除标签？', `确认删除“${item.name}”？`)) return
  try {
    await api(`/api/admin/taxonomy/${active.value}/${item.id}`, { method: 'DELETE' })
    await refresh()
    if ((data.value?.items.length || 0) <= 1 && page.value > 1) page.value--
    message.success('已删除')
  } catch (e) { message.error(errorText(e)) }
}
</script>
<template>
  <div>
    <AdminPageHead title="分类与标签" subtitle="文章用分类整理栏目，标签用于描述内容主题。地址标识自动按数字递增生成。">
      <button class="primary" @click="open()">＋ 新建{{ newLabel }}</button>
    </AdminPageHead>
    <section class="studio-panel">
      <div class="admin-tabs">
        <button :class="{ active: active === 'categories' }" @click="active = 'categories'">文章分类</button>
        <button :class="{ active: active === 'tags' }" @click="active = 'tags'">标签</button>
      </div>
      <form class="admin-filters" @submit.prevent="search = q">
        <input v-model="q" type="search" aria-label="搜索分类或标签" placeholder="搜索名称…" maxlength="100">
        <button>搜索</button>
        <small>共 {{ data?.total || 0 }} 个</small>
      </form>
      <p v-if="error" class="form-error" role="alert">加载失败。<button @click="refresh()">重试</button></p>
      <div v-for="item in data?.items || []" :key="item.id" class="tag-manage-row">
        <span class="tag">{{ item.name }}</span>
        <small>/{{ item.slug }} · {{ item.count || 0 }} 条公开内容</small>
        <button @click="open(item)">编辑</button>
        <button class="danger" @click="remove(item)">删除</button>
      </div>
      <p v-if="!error && !(data?.items.length)" class="admin-empty">{{ emptyText }}</p>
      <Pagination :page="page" :total="data?.total || 0" :size="size" @change="page = $event" />
    </section>
    <NModal v-model:show="show" preset="card" :title="modalTitle" style="width:min(500px,calc(100% - 30px))">
      <form @submit.prevent="save">
        <div class="field">
          <label for="taxon-name">名称</label>
          <input id="taxon-name" v-model="form.name" required maxlength="60">
        </div>
        <div v-if="active === 'categories'" class="field">
          <label for="category-order">排序</label>
          <input id="category-order" v-model.number="form.sortOrder" type="number" min="0" max="10000">
        </div>
        <p class="admin-footnote">{{ editing ? `地址标识 /${editing.slug}，创建后固定。` : '地址标识将自动按数字递增生成，不用填写。' }}</p>
        <button class="primary" :disabled="busy">保存</button>
      </form>
    </NModal>
  </div>
</template>
