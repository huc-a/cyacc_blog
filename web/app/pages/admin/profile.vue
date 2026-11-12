<script setup lang="ts">
import { useMessage } from 'naive-ui'
definePageMeta({ layout: 'admin' }); useSeoMeta({ title: '管理员资料' })
const api = useApi(), message = useMessage(), confirm = useConfirm(), { account, load } = useAuth()
const displayName = ref(account.value?.displayName || ''), avatarAssetId = ref<number | null>(account.value?.avatarAssetId || null), busy = ref(false), uploading = ref(false), passwordBusy = ref(false)
const currentPassword = ref(''), newPassword = ref(''), repeatPassword = ref('')
const dirty = computed(() => displayName.value !== (account.value?.displayName || '') || avatarAssetId.value !== (account.value?.avatarAssetId || null))
useUnsavedChanges(computed(() => dirty.value || uploading.value || !!currentPassword.value || !!newPassword.value || !!repeatPassword.value))
async function save() {
  busy.value = true
  try {
    await api('/api/admin/account/profile', { method: 'PUT', body: { displayName: displayName.value, avatarAssetId: avatarAssetId.value, lockVersion: account.value!.lockVersion } })
    await load(); displayName.value = account.value!.displayName; avatarAssetId.value = account.value!.avatarAssetId
    message.success('管理员资料已保存')
  } catch (e) { message.error(errorText(e)) } finally { busy.value = false }
}
async function changePassword() {
  if (dirty.value || uploading.value || busy.value) { message.warning('请先保存管理员资料，再修改密码'); return }
  if (newPassword.value !== repeatPassword.value) { message.error('两次新密码不一致'); return }
  if (!await confirm('修改管理员密码？', '修改成功后，所有设备上的会话将失效，需要重新登录。')) return
  passwordBusy.value = true
  try {
    await api('/api/admin/account/password', { method: 'POST', body: { currentPassword: currentPassword.value, newPassword: newPassword.value, lockVersion: account.value!.lockVersion } })
    currentPassword.value = ''; newPassword.value = ''; repeatPassword.value = ''
    message.success('密码已修改，请重新登录'); await navigateTo('/admin/login')
  } catch (e) { message.error(errorText(e)) } finally { passwordBusy.value = false }
}
</script>
<template>
  <div>
    <AdminPageHead title="管理员资料" subtitle="后台显示名称和头像独立设置，不影响网站作者资料。">
      <button class="primary" :disabled="busy || uploading || !dirty" @click="save">保存资料</button>
    </AdminPageHead>
    <section class="studio-panel" style="margin-bottom:22px">
      <div class="profile-hero">
        <AdminAvatar />
        <div>
          <h2>{{ account?.displayName || '管理员' }}</h2>
          <p>登录账号 <strong>@{{ account?.username }}</strong> · 这是你在后台的样子：顶栏、侧栏和操作记录里显示的都是它。前台访客看到的作者头像在「网站信息」里维护，两者互不影响。</p>
        </div>
      </div>
    </section>
    <div class="settings-grid">
      <section class="studio-panel">
        <h2>编辑资料</h2>
        <div class="field">
          <label>管理员头像</label>
          <SiteImageControl v-model="avatarAssetId" v-model:busy="uploading" kind="avatar" label="管理员头像" save-hint="保存资料后生效" />
        </div>
        <div class="field">
          <label for="display-name">后台显示名</label>
          <input id="display-name" v-model="displayName" required maxlength="80">
        </div>
        <button class="primary" :disabled="busy || uploading || !dirty" @click="save">{{ busy ? '正在保存…' : '保存管理员资料' }}</button>
      </section>
      <section class="studio-panel">
        <h2>修改密码</h2>
        <form @submit.prevent="changePassword">
          <div class="field"><label for="current-password">当前密码</label><input id="current-password" v-model="currentPassword" type="password" autocomplete="current-password" required maxlength="72"></div>
          <div class="field"><label for="new-password">新密码</label><input id="new-password" v-model="newPassword" type="password" autocomplete="new-password" required minlength="12" maxlength="72"></div>
          <div class="field"><label for="repeat-password">确认新密码</label><input id="repeat-password" v-model="repeatPassword" type="password" autocomplete="new-password" required minlength="12" maxlength="72"></div>
          <button :disabled="passwordBusy || busy || uploading">修改密码并重新登录</button>
        </form>
        <p class="admin-footnote">至少 12 位。修改后会退出所有设备上的登录。</p>
      </section>
    </div>
  </div>
</template>
