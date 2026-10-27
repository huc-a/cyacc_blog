<script setup lang="ts">
import { NModal, useMessage } from 'naive-ui'
import type { Page } from '~/types'
interface AdminAccount { id: number; username: string; displayName: string; enabled: boolean; lastLoginAt: string | null; createdAt: string; lockVersion: number }
definePageMeta({ layout: 'admin' })
useSeoMeta({ title: '账号管理', robots: 'noindex,nofollow' })
const api = useApi(), message = useMessage(), confirm = useConfirm()
const { account } = useAuth()
const page = ref(1), query = computed(() => ({ page: page.value, size: 20 }))
const { data, error, status, refresh } = await useFetch<Page<AdminAccount>>('/api/admin/accounts', { query })
const show = ref(false), busy = ref(false), formError = ref(''), target = ref<AdminAccount | null>(null)
const form = reactive({ username: '', displayName: '', password: '', repeat: '' })
function open(item: AdminAccount | null = null) {
  target.value = item; Object.assign(form, { username: item?.username || '', displayName: '', password: '', repeat: '' })
  formError.value = ''; show.value = true
}
watch(show, value => { if (!value) { form.password = ''; form.repeat = ''; formError.value = '' } })
async function save() {
  if (busy.value) return
  formError.value = ''
  if (form.password !== form.repeat) { formError.value = '两次输入的密码不一致'; return }
  if (new TextEncoder().encode(form.password).length > 72) { formError.value = '密码编码后不能超过 72 字节，中文和特殊符号会占用多个字节'; return }
  busy.value = true
  try {
    if (target.value) await api(`/api/admin/accounts/${target.value.id}/password`, { method: 'POST', body: { newPassword: form.password, lockVersion: target.value.lockVersion } })
    else await api('/api/admin/accounts', { method: 'POST', body: { username: form.username.trim(), displayName: form.displayName.trim(), password: form.password } })
    message.success(target.value ? '密码已重置，该账号需要重新登录' : '账号已添加，可以登录后台')
    show.value = false; await refresh()
  } catch (e) { formError.value = errorText(e); await refresh() } finally { busy.value = false }
}
async function toggle(item: AdminAccount) {
  if (busy.value) return
  if (!await confirm(item.enabled ? '停用这个账号？' : '启用这个账号？', item.enabled
    ? `「${item.username}」将立即退出登录，之后无法进入后台。可随时重新启用。`
    : `「${item.username}」将恢复全部后台管理权限。`)) return
  busy.value = true
  try {
    await api(`/api/admin/accounts/${item.id}/state`, { method: 'PUT', body: { enabled: !item.enabled, lockVersion: item.lockVersion } })
    message.success(item.enabled ? '账号已停用' : '账号已启用')
  } catch (e) { message.error(errorText(e)) } finally { await refresh(); busy.value = false }
}
function date(value: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '尚未登录' }
</script>

<template>
  <div>
    <AdminPageHead title="账号管理" subtitle="所有启用的账号都拥有完整后台权限。"><button class="primary" :disabled="busy" @click="open()">＋ 添加账号</button></AdminPageHead>
    <section class="studio-panel accounts-panel">
      <p class="admin-footnote">停用账号会结束它的登录会话，并保留已有内容与操作记录。自己的资料和密码可在<NuxtLink to="/admin/settings">系统设置</NuxtLink>中修改。</p>
      <p v-if="error" role="alert" class="form-error">账号列表加载失败。<button @click="refresh()">重试</button></p>
      <p v-else-if="status === 'pending' && !data" role="status">正在加载账号…</p>
      <div v-else class="table-scroll">
        <table class="admin-table accounts-table"><thead><tr><th>账号</th><th>显示名</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
          <tbody><tr v-for="item in data?.items" :key="item.id">
            <td data-label="账号"><strong>{{ item.username }}</strong><small v-if="item.username === account?.username">当前账号</small></td>
            <td data-label="显示名">{{ item.displayName }}</td>
            <td data-label="状态"><span class="account-state" :class="{ disabled: !item.enabled }">{{ item.enabled ? '已启用' : '已停用' }}</span></td>
            <td data-label="最近登录">{{ date(item.lastLoginAt) }}</td>
            <td data-label="操作"><div v-if="item.username !== account?.username" class="row-actions"><button :disabled="busy" @click="open(item)">重置密码</button><button :disabled="busy" :class="{ danger: item.enabled }" @click="toggle(item)">{{ item.enabled ? '停用' : '启用' }}</button></div><NuxtLink v-else to="/admin/settings">修改自己的资料 →</NuxtLink></td>
          </tr></tbody>
        </table>
      </div>
      <Pagination :page="page" :size="20" :total="data?.total || 0" @change="page = $event" />
    </section>
    <NModal v-model:show="show" preset="card" :title="target ? '重置账号密码' : '添加后台账号'" :mask-closable="!busy" :close-on-esc="!busy" :closable="!busy" style="width:min(480px,calc(100% - 30px))">
      <form class="account-form" @submit.prevent="save">
        <p v-if="target" class="admin-footnote">重置「{{ target.username }}」的密码后，它在所有设备上都需要重新登录。</p>
        <template v-else>
          <div class="field"><label for="account-username">账号</label><input id="account-username" v-model="form.username" required minlength="3" maxlength="60" pattern="[A-Za-z0-9][A-Za-z0-9_.-]{2,59}" autocomplete="off" autocapitalize="none" spellcheck="false" :disabled="busy"><p class="admin-footnote">3–60 位，以字母或数字开头，可含下划线、点和连字符；创建后固定。</p></div>
          <div class="field"><label for="account-name">显示名</label><input id="account-name" v-model="form.displayName" required maxlength="80" :disabled="busy"></div>
        </template>
        <div class="field"><label for="account-password">{{ target ? '新密码' : '初始密码' }}</label><input id="account-password" v-model="form.password" type="password" autocomplete="new-password" required minlength="12" maxlength="72" :disabled="busy"></div>
        <div class="field"><label for="account-repeat">确认密码</label><input id="account-repeat" v-model="form.repeat" type="password" autocomplete="new-password" required minlength="12" maxlength="72" :disabled="busy"></div>
        <p class="admin-footnote">密码至少 12 位。请将账号和密码交给使用者妥善保管。</p>
        <p v-if="formError" role="alert" class="form-error">{{ formError }}</p>
        <div class="admin-actions"><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : target ? '确认重置密码' : '添加账号' }}</button><button type="button" :disabled="busy" @click="show = false">取消</button></div>
      </form>
    </NModal>
  </div>
</template>

<style scoped>
.accounts-panel>.admin-footnote{margin:0 0 22px;line-height:1.9}.accounts-panel a{color:#567244;text-decoration:underline;text-underline-offset:3px}.accounts-table{min-width:700px}.accounts-table strong{overflow-wrap:anywhere}.account-state{display:inline-block;padding:3px 9px;border-radius:20px;background:#e4ecd8;color:#4e683b}.account-state.disabled{background:#eceae3;color:#79756a}.account-form .field input{width:100%;box-sizing:border-box}.account-form .admin-actions{margin-top:20px}
@media(max-width:760px){.accounts-table{min-width:0}.accounts-table thead{display:none}.accounts-table,.accounts-table tbody,.accounts-table tr{display:block}.accounts-table tr{padding:12px 0;border-bottom:1px solid #e0e6d7}.accounts-table td{display:grid;grid-template-columns:65px minmax(0,1fr);gap:8px;border:0;padding:9px 0;white-space:normal;overflow-wrap:anywhere}.accounts-table td:before{content:attr(data-label);color:#879878}.accounts-table td small{grid-column:2}.accounts-table .row-actions{flex-wrap:wrap}}
</style>
