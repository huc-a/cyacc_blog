import { test, expect } from '@playwright/test'
import { login, mutate } from './helpers'

const gif = Buffer.from('47494638396101000100800000ff00000000ff21ff0b4e45545343415045322e30030100000021f904000a0000002c000000000100010000020244010021f90400140000002c00000000010001000002024c01003b', 'hex')

test('editor header groups actions and stays usable at desktop and mobile widths', async ({ page }) => {
  await login(page.request)
  for (const width of [981, 390]) {
    await page.setViewportSize({ width, height: 828 })
    await page.goto('/admin/editor/new?type=article')
    const bar = page.locator('.editor-topbar')
    await expect(bar).toContainText('新建文章')
    await expect(bar.getByRole('link', { name: '返回列表' })).toHaveAttribute('href', '/admin/articles')
    for (const name of ['专注', '预览', '保存草稿', '发布']) await expect(bar.getByRole('button', { name, exact: true })).toBeVisible()
    expect((await bar.boundingBox())!.height).toBeLessThan(width > 760 ? 85 : 145)
    await bar.getByRole('button', { name: '专注', exact: true }).click()
    await expect(bar.getByRole('button', { name: '退出专注', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await bar.getByRole('button', { name: '退出专注', exact: true }).click()
    await page.getByLabel('标题', { exact: true }).fill('写下今天的新发现')
    await expect(bar.getByRole('status')).toContainText('有未保存修改')
    await page.getByLabel('标题', { exact: true }).fill('')
    await expect(bar.getByRole('status')).toContainText('新草稿')
    await page.evaluate(() => window.scrollTo({ top: 400, behavior: 'instant' }))
    await expect(bar.getByRole('button', { name: '保存草稿', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
    await page.screenshot({ path: `test-results/editor-header-${width}.png`, animations: 'disabled' })
  }
})

test('police placeholder and login wallpaper can be saved selected and removed', async ({ page, request, browser }) => {
  await login(page.request)
  const api = page.request, initial = await (await api.get('/api/admin/site')).json()
  const visitor = await browser.newPage()
  let assetId: number | undefined
  async function saveSite() {
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/admin/site' && r.request().method() === 'PUT')
    await page.getByRole('button', { name: '保存网站设置', exact: true }).click()
    expect((await response).ok()).toBeTruthy()
    await expect(page.getByRole('button', { name: '保存网站设置', exact: true })).toBeDisabled()
  }
  try {
    await page.setViewportSize({ width: 981, height: 828 }); await page.goto('/admin/settings')
    await expect(page.getByLabel('公安备案号', { exact: true })).toHaveAttribute('placeholder', /办理中/)
    const filename = `login-wallpaper-${Date.now()}.gif`
    await page.getByLabel('上传自定义登录页壁纸', { exact: true }).setInputFiles({ name: filename, mimeType: 'image/gif', buffer: gif })
    const preview = page.getByRole('img', { name: '自定义登录页壁纸预览', exact: true })
    await expect(preview).toBeVisible()
    assetId = Number((await preview.getAttribute('src'))!.match(/media\/(\d+)/)![1])
    expect((await (await request.get('/api/public/site')).json()).loginBackgroundAssetId).toBe(initial.settings.loginBackgroundAssetId)
    await page.getByLabel('公安备案号', { exact: true }).fill('桂公网安备45010302001234号')
    await saveSite()
    await expect(page.locator('.n-message-container')).toContainText('网站信息已保存')
    await expect(page.getByRole('button', { name: '保存网站设置', exact: true })).toBeDisabled()
    await page.reload(); await expect(preview).toHaveAttribute('src', `/api/admin/media/${assetId}/file`)
    await expect(page.getByLabel('公安备案号', { exact: true })).toHaveValue('桂公网安备45010302001234号')
    for (const width of [981, 390]) {
      await visitor.setViewportSize({ width, height: 828 }); await visitor.goto('/')
      await expect(visitor.locator('.police-record')).toHaveAttribute('href', 'https://beian.mps.gov.cn/#/query/webSearch?code=45010302001234')
      await visitor.goto('/admin/login')
      await expect(visitor.locator('.login-screen .landscape')).toHaveCSS('background-image', new RegExp(`/api/public/media/${assetId}`))
      expect(await visitor.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    }
    const csrf = await (await api.get('/api/auth/csrf')).json()
    expect((await api.delete(`/api/admin/media/${assetId}`, { headers: { [csrf.headerName]: csrf.token } })).status()).toBe(409)
    await page.getByLabel('公安备案号', { exact: true }).fill('')
    await page.getByRole('button', { name: '移除登录页壁纸', exact: true }).click()
    await saveSite()
    await expect(page.getByRole('button', { name: '保存网站设置', exact: true })).toBeDisabled()
    await visitor.goto('/'); await expect(visitor.locator('.police-record')).toHaveText('公安备案办理中')
    await expect(visitor.locator('a.police-record')).toHaveCount(0)
    await visitor.locator('.site-footer').scrollIntoViewIfNeeded()
    await visitor.screenshot({ path: 'test-results/police-placeholder.png', animations: 'disabled' })
    for (const width of [981, 390]) {
      await visitor.setViewportSize({ width, height: 828 }); await visitor.goto('/admin/login')
      await expect(visitor.locator('.login-screen .landscape')).toHaveCSS('background-image', new RegExp(initial.settings.backgroundAssetId ? `/api/public/media/${initial.settings.backgroundAssetId}` : `/backgrounds/${initial.settings.background}.svg`))
      await visitor.screenshot({ path: `test-results/login-wallpaper-${width}.png`, animations: 'disabled' })
    }
    expect((await request.get(`/api/public/media/${assetId}`)).status()).toBe(404)
    await page.getByRole('button', { name: '从媒体库选择登录页壁纸', exact: true }).click()
    await page.locator('.picker-grid').getByRole('button', { name: filename }).click()
    await saveSite()
    await expect(page.getByRole('button', { name: '保存网站设置', exact: true })).toBeDisabled()
    expect((await (await request.get('/api/public/site')).json()).loginBackgroundAssetId).toBe(assetId)
  } finally {
    const current = await (await api.get('/api/admin/site')).json()
    await mutate(api, 'PUT', '/api/admin/site', { settings: initial.settings, lockVersion: current.lockVersion })
    if (assetId) await mutate(api, 'DELETE', `/api/admin/media/${assetId}`)
    await visitor.close()
  }
})

test('administrator avatar persists privately and can be selected or removed', async ({ page, request }) => {
  await login(page.request)
  const api = page.request, initial = await (await api.get('/api/auth/me')).json()
  let assetId: number | undefined
  try {
    await page.goto('/admin/settings')
    const filename = `admin-avatar-${Date.now()}.gif`
    await page.getByLabel('上传自定义管理员头像', { exact: true }).setInputFiles({ name: filename, mimeType: 'image/gif', buffer: gif })
    const preview = page.getByRole('img', { name: '自定义管理员头像预览', exact: true })
    await expect(preview).toBeVisible()
    assetId = Number((await preview.getAttribute('src'))!.match(/media\/(\d+)/)![1])
    expect((await (await api.get('/api/auth/me')).json()).avatarAssetId).toBe(initial.avatarAssetId)
    await page.getByRole('button', { name: '保存管理员资料', exact: true }).click()
    await expect(page.getByRole('button', { name: '保存管理员资料', exact: true })).toBeDisabled()
    await expect(page.locator('.account-avatar img')).toHaveCount(2)
    for (const avatar of await page.locator('.account-avatar img').all()) await expect(avatar).toHaveAttribute('src', `/api/admin/media/${assetId}/file`)
    expect((await request.get(`/api/public/media/${assetId}`)).status()).toBe(404)
    expect((await request.get(`/api/admin/media/${assetId}/file`)).status()).toBe(401)
    await page.reload(); await expect(preview).toHaveAttribute('src', `/api/admin/media/${assetId}/file`)
    const csrf = await (await api.get('/api/auth/csrf')).json()
    expect((await api.delete(`/api/admin/media/${assetId}`, { headers: { [csrf.headerName]: csrf.token } })).status()).toBe(409)
    await page.getByRole('button', { name: '移除管理员头像', exact: true }).click()
    await page.getByRole('button', { name: '保存管理员资料', exact: true }).click()
    await expect(page.getByRole('button', { name: '保存管理员资料', exact: true })).toBeDisabled()
    await expect(page.locator('.account-avatar img')).toHaveCount(0)
    await page.getByRole('button', { name: '从媒体库选择管理员头像', exact: true }).click()
    await page.locator('.picker-grid').getByRole('button', { name: filename }).click()
    await page.getByRole('button', { name: '保存管理员资料', exact: true }).click()
    await expect(page.getByRole('button', { name: '保存管理员资料', exact: true })).toBeDisabled()
    for (const width of [981, 390]) {
      await page.setViewportSize({ width, height: 828 })
      await page.getByRole('heading', { name: '管理员资料', exact: true }).scrollIntoViewIfNeeded()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
      await page.screenshot({ path: `test-results/admin-profile-${width}.png`, animations: 'disabled' })
    }
  } finally {
    const current = await (await api.get('/api/auth/me')).json()
    await mutate(api, 'PUT', '/api/admin/account/profile', { displayName: initial.displayName, avatarAssetId: initial.avatarAssetId, lockVersion: current.lockVersion })
    if (assetId) await mutate(api, 'DELETE', `/api/admin/media/${assetId}`)
  }
})
