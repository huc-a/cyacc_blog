import type { Account } from '~/types'
export function useAuth() {
  const account = useState<Account | null>('admin-account', () => null)
  const api = useApi()
  async function load() { account.value = await api<Account>('/api/auth/me'); return account.value }
  async function logout() { await api('/api/auth/logout', { method: 'POST' }); account.value = null; await navigateTo('/admin/login') }
  return { account, load, logout }
}
