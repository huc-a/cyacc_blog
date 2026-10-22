import { test, expect, type APIRequestContext } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
export function credentials() {
  if (process.env.E2E_USERNAME && process.env.E2E_PASSWORD) return { username: process.env.E2E_USERNAME, password: process.env.E2E_PASSWORD }
  return JSON.parse(readFileSync(resolve('../.local/admin-credentials.json'), 'utf8').replace(/^\uFEFF/, '')) as { username: string; password: string }
}
export async function login(request: APIRequestContext) {
  async function attempt() {
    const csrf = await (await request.get('/api/auth/csrf')).json()
    return request.post('/api/auth/login', { form: credentials(), headers: { [csrf.headerName]: csrf.token } })
  }
  let response = await attempt()
  if (response.status() === 429) {
    // Respect the live server's throttle when a suite needs more fixture sessions.
    const seconds = Number(response.headers()['retry-after'])
    expect(seconds).toBeGreaterThan(0); expect(seconds).toBeLessThanOrEqual(60)
    test.setTimeout(test.info().timeout + seconds * 1000 + 5000)
    await new Promise(resolve => setTimeout(resolve, seconds * 1000))
    response = await attempt()
  }
  expect(response.ok(), `Login returned ${response.status()}: ${await response.text()}`).toBeTruthy()
}
export async function mutate(request: APIRequestContext, method: 'POST' | 'PUT' | 'DELETE', path: string, data?: unknown) {
  const csrf = await (await request.get('/api/auth/csrf')).json()
  const response = await request.fetch(path, { method, data, headers: { [csrf.headerName]: csrf.token } })
  expect(response.ok(), `${path}: ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return text ? JSON.parse(text) : null
}
