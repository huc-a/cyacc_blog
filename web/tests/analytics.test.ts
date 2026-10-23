import { afterEach, describe, expect, it, vi } from 'vitest'

async function setup(doNotTrack = '0', scriptUrl = 'https://cloud.umami.is/script.js') {
  vi.resetModules()
  const route = { path: '/', fullPath: '/?private=hidden' }
  const script: Record<string, any> = { dataset: {} }
  const appendChild = vi.fn()
  let finish: (() => void) | undefined
  const payloads: Record<string, unknown>[] = []
  const tracker = { track: (customize: (value: Record<string, unknown>) => Record<string, unknown>) => payloads.push(customize({ url: '/?private=hidden', referrer: 'https://example.org/path?secret=hidden' })) }
  const windowMock: { umami?: typeof tracker } = {}
  vi.stubGlobal('navigator', { doNotTrack })
  vi.stubGlobal('window', windowMock)
  vi.stubGlobal('document', { referrer: 'https://example.org/path?secret=hidden', createElement: () => script, head: { appendChild } })
  vi.stubGlobal('useRuntimeConfig', () => ({ public: { umamiScriptUrl: scriptUrl, umamiWebsiteId: 'fixture-website' } }))
  vi.stubGlobal('useRouter', () => ({ currentRoute: { value: route } }))
  vi.stubGlobal('defineNuxtPlugin', (plugin: (app: unknown) => unknown) => plugin({ hook: (_name: string, callback: () => void) => { finish = callback } }))
  await import('../app/plugins/analytics.client')
  return { route, script, appendChild, payloads, finish: () => finish?.(), load: () => { windowMock.umami = tracker; script.onload?.() } }
}

afterEach(() => vi.unstubAllGlobals())
describe('Umami collection', () => {
  it('tracks once after script load and strips query and referrer details', async () => {
    const state = await setup()
    state.finish() // Nuxt can finish rendering before the external script loads.
    state.load()
    state.finish()
    expect(state.appendChild).toHaveBeenCalledTimes(1)
    expect(state.script.dataset.autoTrack).toBe('false')
    expect(state.payloads).toEqual([{ url: '/', referrer: 'https://example.org' }])
    state.route.path = '/articles'; state.route.fullPath = '/articles?search=hidden'
    state.finish()
    expect(state.payloads).toHaveLength(2)
    expect(state.payloads[1]?.url).toBe('/articles')
    expect(JSON.stringify(state.payloads)).not.toContain('hidden')
  })
  it('excludes admin navigation and resumes tracking on return to the public site', async () => {
    const state = await setup()
    state.route.path = '/admin/login'; state.route.fullPath = '/admin/login'
    state.load(); state.finish()
    expect(state.payloads).toHaveLength(0)
    state.route.path = '/'; state.route.fullPath = '/'
    state.finish()
    expect(state.payloads).toHaveLength(1)
    state.route.path = '/admin/media'; state.route.fullPath = '/admin/media'; state.finish()
    state.route.path = '/'; state.route.fullPath = '/'; state.finish()
    expect(state.payloads).toHaveLength(2)
  })
  it('does not load a tracker for Do Not Track or an unconfigured site', async () => {
    expect((await setup('1')).appendChild).not.toHaveBeenCalled()
    expect((await setup('0', '')).appendChild).not.toHaveBeenCalled()
  })
})
