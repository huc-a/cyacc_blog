import { test, expect } from '@playwright/test'
import { login, mutate } from './helpers'

test('navigation search opens left, closes accessibly, and gains opacity on scroll', async ({ page, request }) => {
  const site = await (await request.get('/api/public/site')).json()
  await page.setViewportSize({ width: 1082, height: 828 })
  await page.goto('/')
  const header = page.locator('.nav-shell'), trigger = page.getByRole('button', { name: '搜索', exact: true })
  await expect(page.getByRole('button', { name: '清晰阅读' })).toHaveCount(0)
  await expect(page.locator('.announcement, .profile-counts')).toHaveCount(0)
  await expect(page.locator('.desktop-nav .site-icon')).toHaveCount(6)
  if (site.github) await expect(page.locator('.profile-contacts a').filter({ hasText: 'GitHub' })).toHaveAttribute('href', site.github)
  if (site.email) await expect(page.locator('.profile-contacts a').filter({ hasText: '邮箱' })).toHaveAttribute('href', `mailto:${site.email}`)
  if (site.icpNumber) { await expect(page.locator('.icp-record')).toHaveText(site.icpNumber); await expect(page.locator('.icp-record')).toHaveAttribute('href', 'https://beian.miit.gov.cn/') }
  const card = await page.locator('.profile').boundingBox(), art = await page.locator('.profile-art').boundingBox()
  expect(art!.height / card!.height).toBeGreaterThan(.5)
  const topColor = await header.evaluate(el => getComputedStyle(el).backgroundColor)
  expect(topColor).toBe(`rgba(250, 247, 235, ${site.navTopOpacity})`)
  await trigger.click()
  const field = page.getByRole('searchbox', { name: '搜索内容' })
  await expect(field).toBeFocused()
  await expect(page.locator('.header-search-panel')).toHaveCSS('opacity', '1')
  const panel = await page.locator('.header-search-panel').boundingBox(), button = await trigger.boundingBox()
  expect(panel!.x).toBeGreaterThanOrEqual(0)
  expect(panel!.x + panel!.width).toBeLessThan(button!.x)
  await page.screenshot({ path: 'test-results/home-search-left.png' })
  await page.keyboard.press('Escape'); await expect(field).toHaveCount(0); await expect(trigger).toBeFocused()
  await trigger.click(); await page.locator('.hero h1').click(); await expect(field).toHaveCount(0)
  await page.evaluate(() => window.scrollTo({ top: 500, behavior: 'instant' }))
  await expect(header).toHaveClass(/scrolled/)
  await expect.poll(() => header.evaluate(el => getComputedStyle(el).backgroundColor)).toBe(`rgba(250, 247, 235, ${site.navScrolledOpacity})`)
  expect((await header.boundingBox())!.y).toBe(0)
  await page.screenshot({ path: 'test-results/home-scrolled.png' })
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
  await expect(header).not.toHaveClass(/scrolled/)
  await page.screenshot({ path: 'test-results/home-desktop.png', fullPage: true, animations: 'disabled' })
})

test('mobile profile, search and navigation fit the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/')
  await expect(page.locator('.profile')).toBeVisible()
  const art = await page.locator('.profile-art').boundingBox(), card = await page.locator('.profile').boundingBox()
  expect((art!.width * art!.height) / (card!.width * card!.height)).toBeGreaterThan(.5)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  const panel = await page.locator('.header-search-panel').boundingBox()
  expect(panel!.x).toBeGreaterThanOrEqual(12)
  expect(panel!.x + panel!.width).toBeLessThan(390)
  await page.getByRole('searchbox', { name: '搜索内容' }).fill('不会匹配的验收词')
  await expect(page.locator('.header-search-panel')).toHaveCSS('opacity', '1')
  await page.screenshot({ path: 'test-results/home-mobile-search.png' })
  await page.getByRole('button', { name: '提交搜索' }).click()
  await expect(page).toHaveURL(/\/search\?q=/)
  await expect(page.getByText('暂时没有找到')).toBeVisible()
  await page.goto('/'); await page.getByRole('button', { name: '打开导航' }).click()
  await page.getByRole('navigation', { name: '移动导航' }).getByRole('link', { name: '首页', exact: true }).click()
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByRole('navigation', { name: '移动导航' })).toHaveCount(0)
  await page.getByRole('button', { name: '关闭搜索' }).click()
  await expect(page.locator('.header-search-panel')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: 'test-results/home-mobile.png', fullPage: true, animations: 'disabled' })
})

test('custom wallpaper and animated avatar upload, preview, apply and reference protection', async ({ page, request }) => {
  await login(page.request)
  const api = page.request, initial = await (await api.get('/api/admin/site')).json(), assets: number[] = []
  // Two 1x1 GIF frames with distinct colors, delays and infinite looping.
  const gif = Buffer.from('47494638396101000100800000ff00000000ff21ff0b4e45545343415045322e30030100000021f904000a0000002c000000000100010000020244010021f90400140000002c00000000010001000002024c01003b', 'hex')
  try {
    await page.goto('/admin/settings')
    await page.getByLabel('GitHub 地址').fill('https://github.com/huc-a')
    await page.getByLabel('联系邮箱').fill('19977942403@163.com')
    await page.getByLabel('ICP 备案号').fill('桂ICP备2026024037号')
    // A clean local site may already have these values; only save an actual edit.
    const save = page.getByRole('button', { name: '保存网站设置' })
    if (await save.isEnabled()) { await save.click(); await expect(page.locator('.n-message-container')).toContainText('网站信息已保存') }
    await page.goto('/admin/appearance')
    for (const [label, name, mimeType, buffer] of [
      ['上传自定义壁纸', 'wallpaper.png', 'image/png', Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNImNAAAALEAXGxfUYHAAAAAElFTkSuQmCC', 'base64')],
      ['上传自定义头像', 'avatar.gif', 'image/gif', gif]
    ] as const) {
      const response = page.waitForResponse(r => r.url().endsWith('/api/admin/media') && r.request().method() === 'POST')
      await page.getByLabel(label).setInputFiles({ name, mimeType, buffer })
      const upload = await response; expect(upload.ok(), await upload.text()).toBeTruthy()
      assets.push((await upload.json()).id)
    }
    await expect(page.locator('.preview-profile img')).toHaveAttribute('src', `/api/admin/media/${assets[1]}/file`)
    expect((await (await request.get('/api/public/site')).json()).avatarAssetId).toBe(initial.settings.avatarAssetId)
    expect((await request.get(`/api/public/media/${assets[1]}`)).status()).toBe(404)
    await page.getByRole('button', { name: '应用更改' }).click()
    await expect(page.getByRole('button', { name: '应用更改' })).toBeDisabled()
    await expect(page.locator('.n-message-container')).toContainText('外观已应用到前台')
    await page.reload(); await expect(page.getByAltText('自定义头像预览')).toHaveAttribute('src', `/api/admin/media/${assets[1]}/file`)
    await page.screenshot({ path: 'test-results/appearance-custom-images.png', fullPage: true })
    await page.goto('/')
    await expect(page.locator('.profile-contacts a[href="https://github.com/huc-a"]')).toBeVisible()
    await expect(page.locator('.profile-contacts a[href="mailto:19977942403@163.com"]')).toBeVisible()
    await expect(page.locator('.icp-record')).toHaveText('桂ICP备2026024037号')
    await expect(page.locator('.profile-art img')).toHaveAttribute('src', `/api/public/media/${assets[1]}`)
    expect(await page.locator('.profile-art img').evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBeTruthy()
    expect(await page.locator('.landscape').evaluate(el => getComputedStyle(el).backgroundImage)).toContain(`/api/public/media/${assets[0]}`)
    const image = await request.get(`/api/public/media/${assets[1]}`)
    expect(image.headers()['content-type']).toContain('image/gif')
    const bytes = await image.body()
    // Each re-encoded frame retains a Graphic Control Extension.
    expect(bytes.toString('hex').match(/21f904/g)?.length).toBe(2)
    const csrf = await (await api.get('/api/auth/csrf')).json()
    expect((await api.delete(`/api/admin/media/${assets[1]}`, { headers: { [csrf.headerName]: csrf.token } })).status()).toBe(409)
    await page.goto('/about'); await expect(page.locator('.about-avatar')).toHaveAttribute('src', `/api/public/media/${assets[1]}`)
    await page.goto('/admin/settings'); await expect(page.getByLabel('ICP 备案号')).toHaveValue('桂ICP备2026024037号')
  } finally {
    const current = await (await api.get('/api/admin/site')).json()
    await mutate(api, 'PUT', '/api/admin/site', { settings: initial.settings, lockVersion: current.lockVersion })
    for (const id of assets) { expect((await request.get(`/api/public/media/${id}`)).status()).toBe(404); await mutate(api, 'DELETE', `/api/admin/media/${id}`) }
  }
})
