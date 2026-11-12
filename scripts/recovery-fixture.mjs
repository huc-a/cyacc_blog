// Local/CI recovery verification. Only loopback destinations are accepted.
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
const mode = process.argv[2], base = new URL(process.env.RECOVERY_API || 'http://127.0.0.1:8080')
if (!['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname)) throw Error('Recovery fixtures only run against loopback')
const credentials = process.env.E2E_PASSWORD ? { username: process.env.E2E_USERNAME || 'admin', password: process.env.E2E_PASSWORD } : JSON.parse(readFileSync('.local/admin-credentials.json', 'utf8').replace(/^\uFEFF/, ''))
const statePath = resolve('.local/recovery-fixture.json'), cookies = new Map()
async function api(path, init = {}, retry = true) {
  const response = await fetch(new URL(path, base), { ...init, headers: { ...init.headers, Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; ') } })
  for (const cookie of response.headers.getSetCookie()) { const pair = cookie.split(';')[0], i = pair.indexOf('='); cookies.set(pair.slice(0, i), pair.slice(i + 1)) }
  if (response.status === 429 && path === '/api/auth/login' && retry) {
    console.log('Respecting login rate limit before recovery verification')
    await new Promise(resolve => setTimeout(resolve, Math.min(60, Number(response.headers.get('Retry-After')) || 60) * 1000))
    return api(path, init, false)
  }
  if (!response.ok) throw Error(`${path}: HTTP ${response.status}`)
  return response
}
async function json(path) { return (await api(path)).json() }
async function change(method, path, value) {
  const csrf = await json('/api/auth/csrf')
  const response = await api(path, { method, headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(value) })
  return response.status === 204 ? null : response.json()
}
const csrf = await json('/api/auth/csrf')
await api('/api/auth/login', { method: 'POST', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(credentials) })
if (mode === 'prepare') {
  const original = await json('/api/admin/site'), token = `recovery-${Date.now()}`, state = { original, items: [] }
  const form = new FormData(); form.append('file', new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNImNAAAALEAXGxfUYHAAAAAElFTkSuQmCC', 'base64')], { type: 'image/png' }), `${token}.png`)
  const csrf = await json('/api/auth/csrf')
  state.asset = await (await api('/api/admin/media', { method: 'POST', headers: { [csrf.headerName]: csrf.token }, body: form })).json()
  for (const type of ['article']) {
    let item = await change('POST', '/api/admin/contents', { type, slug: `${token}-${type}`, title: `恢复演练 ${type}`, summary: '临时验收数据，演练后进入回收站', bodyMarkdown: '恢复演练正文', lockVersion: 0, coverAssetId: state.asset.id })
    item = await change('POST', `/api/admin/contents/${item.id}/publish`, { lockVersion: item.lockVersion }); state.items.push(item)
  }
  await change('PUT', '/api/admin/site', { settings: { ...original.settings, backgroundAssetId: state.asset.id }, lockVersion: original.lockVersion })
  writeFileSync(statePath, JSON.stringify(state, null, 2)); console.log('Prepared published article, cover and background references')
} else {
  const state = JSON.parse(readFileSync(statePath, 'utf8'))
  if (mode === 'verify') {
    const assert = (condition, text) => { if (!condition) throw Error(text) }
    assert((await json('/api/auth/me')).username === credentials.username, 'Restored administrator login')
    for (const item of state.items) {
      const restored = await json(`/api/public/contents/${item.slug}`)
      assert(restored.bodyMarkdown === '恢复演练正文' && restored.coverAssetId === state.asset.id, 'Restored public revision and cover')
    }
    assert((await json('/api/public/site')).backgroundAssetId === state.asset.id, 'Restored background setting')
    const bytes = new Uint8Array(await (await api(`/api/public/media/${state.asset.id}`)).arrayBuffer())
    assert(bytes.length > 20, 'Restored public media file')
    console.log('Recovery verified: administrator login, published revisions, cover, background and media bytes')
  } else if (mode === 'cleanup') {
    const current = await json('/api/admin/site')
    await change('PUT', '/api/admin/site', { settings: state.original.settings, lockVersion: current.lockVersion })
    for (const item of state.items) { const current = await json(`/api/admin/contents/${item.id}`); if (!current.deletedAt) await change('POST', `/api/admin/contents/${item.id}/trash`, { lockVersion: current.lockVersion }) }
    console.log('Restored original appearance and moved recovery fixtures to trash')
  } else throw Error('Use prepare, verify or cleanup')
}
