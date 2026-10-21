import type { SiteSettings } from '~/types'
export const useSite = () => useFetch<SiteSettings>('/api/public/site', { key: 'site-settings' })
