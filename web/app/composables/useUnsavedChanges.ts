export function useUnsavedChanges(dirty: Ref<boolean> | ComputedRef<boolean>) {
  const confirm = useConfirm()
  onBeforeRouteLeave(async () => !dirty.value || await confirm('离开当前页面？', '还有未保存的修改。离开后，这些修改将丢失。'))
  const beforeUnload = (event: BeforeUnloadEvent) => { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
}
