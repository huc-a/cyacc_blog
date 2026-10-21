export function useApi() {
  const request = useRequestFetch()
  return async <T = unknown>(url: string, options: Record<string, unknown> = {}): Promise<T> => {
    const method = String(options.method || 'GET').toUpperCase()
    let headers: Record<string, string> = {}
    if (!['GET', 'HEAD'].includes(method)) {
      const csrf = await request<{ token: string; headerName: string }>('/api/auth/csrf')
      headers = { [csrf.headerName]: csrf.token }
    }
    return request<T>(url, { ...options, headers: { ...headers, ...(options.headers as Record<string, string> || {}) } } as never) as Promise<T>
  }
}
export function errorText(error: unknown) {
  const e = error as { data?: { message?: string }; message?: string; statusCode?: number }
  return e?.data?.message || (e?.statusCode === 401 ? '登录已失效，请重新登录' : '请求失败，请稍后重试')
}
