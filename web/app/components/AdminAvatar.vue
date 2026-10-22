<script setup lang="ts">
const { account } = useAuth()
</script>
<template><span class="account-avatar"><img v-if="account?.avatarAssetId" :src="`/api/admin/media/${account.avatarAssetId}/file`" alt="管理员头像"><span v-else aria-hidden="true">{{ account?.displayName?.slice(0, 1) || '菜' }}</span></span></template>
