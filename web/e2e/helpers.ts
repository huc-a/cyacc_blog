import { expect, type APIRequestContext } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
export function credentials() {
  if (process.env.E2E_USERNAME && process.env.E2E_PASSWORD) return { username: process.env.E2E_USERNAME, password: process.env.E2E_PASSWORD }
  return JSON.parse(readFileSync(resolve('../.local/admin-credentials.json'), 'utf8').replace(/^\uFEFF/, '')) as { username: string; password: string }
}
export async function login(request: APIRequestContext) {
  const csrf = await (await request.get('/api/auth/csrf')).json()
  const response = await request.post('/api/auth/login', { form: credentials(), headers: { [csrf.headerName]: csrf.token } })
  expect(response.ok()).toBeTruthy()
}
export async function mutate(request: APIRequestContext, method: 'POST' | 'PUT' | 'DELETE', path: string, data?: unknown) {
  const csrf = await (await request.get('/api/auth/csrf')).json()
  const response = await request.fetch(path, { method, data, headers: { [csrf.headerName]: csrf.token } })
  expect(response.ok(), `${path}: ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return text ? JSON.parse(text) : null
}
