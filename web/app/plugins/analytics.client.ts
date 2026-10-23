export default defineNuxtPlugin((app) => {
  const config = useRuntimeConfig()
  if (!config.public.umamiScriptUrl || !config.public.umamiWebsiteId || navigator.doNotTrack === '1') return
  const router = useRouter()
  let trackedRoute: string | null = null
  type Tracker = { track: (customize: (props: Record<string, unknown>) => Record<string, unknown>) => void }
  function track() {
    const path = router.currentRoute.value.path
    if (path.startsWith('/admin')) { trackedRoute = null; return }
    const tracker = (window as Window & { umami?: Tracker }).umami
    if (!tracker || trackedRoute === router.currentRoute.value.fullPath) return
    tracker?.track(props => ({ ...props, url: path, referrer: document.referrer ? new URL(document.referrer).origin : '' }))
    trackedRoute = router.currentRoute.value.fullPath
  }
  const script = document.createElement('script')
  script.src = config.public.umamiScriptUrl
  script.dataset.websiteId = config.public.umamiWebsiteId
  script.dataset.autoTrack = 'false'
  script.defer = true
  script.onload = track
  document.head.appendChild(script)
  app.hook('page:finish', track)
})
