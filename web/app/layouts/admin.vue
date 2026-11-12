<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, zhCN, dateZhCN } from 'naive-ui'
const overrides = { common: { primaryColor: '#54703f', primaryColorHover: '#5f7d47', primaryColorPressed: '#435c33', borderRadius: '10px', fontFamily: 'var(--ui-font)' } }
useSeoMeta({ robots: 'noindex,nofollow', titleTemplate: title => `${title || '管理后台'} · 菜牙爱吃菜` })
</script>
<template><NConfigProvider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="overrides"><NMessageProvider><NDialogProvider><AdminShell><slot /></AdminShell></NDialogProvider></NMessageProvider></NConfigProvider></template>
