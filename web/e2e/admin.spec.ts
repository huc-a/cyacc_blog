import { test, expect, type Page } from '@playwright/test'
import { login, mutate } from './helpers'

async function uiLogin(page: Page) {
  await login(page.request)
  await page.goto('/admin/dashboard')
  await expect(page.locator('.admin-content')).toBeVisible()
}
async function confirm(page: Page) { await page.getByRole('button', { name: '确认', exact: true }).click() }
async function saved(page: Page) { await expect(page.locator('.editor-topbar')).toContainText('已保存') }

test('A/C: UI draft, preview, publish, frozen public version, trash, restore and logout', async ({ page, request }) => {
  const slug = `admin-flow-${Date.now()}`, title = `后台验收 ${slug}`, errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  let id: string | undefined
  await uiLogin(page)
  try {
    await page.goto('/admin/editor/new?type=article')
    await page.getByLabel('标题', { exact: true }).fill(title)
    await page.getByLabel('摘要', { exact: true }).fill('通过真实浏览器完成发布流程')
    await page.getByLabel('访问地址').fill(slug)
    await page.getByRole('textbox', { name: '正文', exact: true }).fill('## 第一版\n\n公开版本一。')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page).toHaveURL(/\/admin\/editor\/\d+/); id = page.url().split('/').pop()
    await saved(page)
    expect((await request.get(`/api/public/contents/${slug}`)).status()).toBe(404)
    await page.getByRole('button', { name: '预览', exact: true }).click()
    await expect(page.locator('.draft-preview')).toContainText('公开版本一')
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: '发布', exact: true }).click(); await confirm(page)
    await expect(page.locator('.editor-topbar .state-badge')).toHaveText('已发布')
    await expect.poll(async () => (await (await request.get(`/api/public/contents/${slug}`)).json()).bodyMarkdown).toContain('公开版本一')
    await page.getByRole('textbox', { name: '正文', exact: true }).fill('## 第二版\n\n仍在修改的第二版。')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click(); await saved(page)
    expect((await (await request.get(`/api/public/contents/${slug}`)).json()).bodyMarkdown).toContain('公开版本一')
    await page.screenshot({ path: 'test-results/admin-editor-desktop.png', fullPage: true })
    await page.goto('/admin/articles')
    await page.getByRole('searchbox', { name: '搜索管理内容' }).fill(slug)
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: title })
    await row.getByRole('button', { name: '回收', exact: true }).click(); await confirm(page)
    await expect(row).toHaveCount(0)
    expect((await request.get(`/api/public/contents/${slug}`)).status()).toBe(404)
    await page.getByRole('button', { name: '回收站', exact: true }).click()
    await row.getByRole('button', { name: '恢复', exact: true }).click(); await confirm(page)
    await expect(row).toHaveCount(0)
    expect((await request.get(`/api/public/contents/${slug}`)).status()).toBe(404)
    await page.goto(`/admin/editor/${id}`)
    await page.getByRole('button', { name: '发布', exact: true }).click(); await confirm(page)
    await expect(page.locator('.editor-topbar .state-badge')).toHaveText('已发布')
    await expect.poll(async () => (await (await request.get(`/api/public/contents/${slug}`)).json()).bodyMarkdown).toContain('仍在修改的第二版')
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await expect(page).toHaveURL(/\/admin\/login/)
    expect((await page.request.get(`/api/admin/contents/${id}`)).status()).toBe(401)
    expect(errors).toEqual([])
  } finally {
    if (id) { await login(request); const item = await (await request.get(`/api/admin/contents/${id}`)).json(); if (!item.deletedAt) await mutate(request, 'POST', `/api/admin/contents/${id}/trash`, { lockVersion: item.lockVersion }) }
  }
})

test('B: upload, appearance preview/apply, and referenced media protection', async ({ page, request }) => {
  await uiLogin(page)
  const api = page.request, initial = await (await api.get('/api/admin/site')).json()
  let asset: any
  try {
    await page.goto('/admin/media')
    const response = page.waitForResponse(r => r.url().endsWith('/api/admin/media') && r.request().method() === 'POST')
    // Valid 1x1 PNG; the server decodes and re-encodes the image.
    const name = `background-${Date.now()}.png`
    await page.getByLabel('上传图片文件').setInputFiles({ name, mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNImNAAAALEAXGxfUYHAAAAAElFTkSuQmCC', 'base64') })
    const upload = await response; expect(upload.ok(), await upload.text()).toBeTruthy(); asset = await upload.json()
    await expect(page.getByRole('heading', { name })).toBeVisible()
    expect((await request.get(`/api/public/media/${asset.id}`)).status()).toBe(404)
    await page.goto('/admin/appearance')
    await page.getByRole('button', { name: '从媒体库选择背景' }).click()
    await page.locator('.picker-grid').getByRole('button', { name }).click()
    await page.getByLabel('首屏文案').fill('外观验收：预览与应用')
    await expect(page.locator('.preview-shell')).toContainText('外观验收：预览与应用')
    expect((await (await request.get('/api/public/site')).json()).headline).toBe(initial.settings.headline)
    await page.getByRole('button', { name: '应用更改' }).click()
    await expect(page.locator('.n-message-container')).toContainText('外观已应用到前台')
    await expect(page.getByRole('button', { name: '应用更改' })).toBeDisabled()
    expect((await (await request.get('/api/public/site')).json()).backgroundAssetId).toBe(asset.id)
    expect((await request.get(`/api/public/media/${asset.id}`)).status()).toBe(200)
    await page.screenshot({ path: 'test-results/admin-appearance-desktop.png', fullPage: true })
    await page.goto('/admin/media')
    await page.locator('.media-card').filter({ hasText: name }).getByRole('button', { name: '删除', exact: true }).click(); await confirm(page)
    await expect(page.locator('.n-message-container')).toContainText(/引用|使用/)
    expect((await api.get(`/api/admin/media/${asset.id}/file`)).status()).toBe(200)
  } finally {
    const current = await (await api.get('/api/admin/site')).json()
    await mutate(api, 'PUT', '/api/admin/site', { settings: initial.settings, lockVersion: current.lockVersion })
    if (asset) await mutate(api, 'DELETE', `/api/admin/media/${asset.id}`)
  }
})

test('tutorial chapter order, unsaved guard, optimistic conflict, admin pages and mobile navigation', async ({ page, request }) => {
  await uiLogin(page)
  const slug = `editor-check-${Date.now()}`; let id: string | undefined
  try {
    await page.goto('/admin/editor/new?type=tutorial')
    await page.getByLabel('标题', { exact: true }).fill(slug)
    await page.getByLabel('访问地址').fill(slug)
    await page.getByRole('textbox', { name: '正文', exact: true }).fill('教程介绍')
    await page.getByRole('button', { name: '＋ 添加章节', exact: true }).click()
    await page.getByLabel('章节标题').fill('先写的章节'); await page.getByLabel('章节正文').fill('第一章正文')
    await page.getByRole('button', { name: '＋ 添加章节', exact: true }).click()
    await page.getByLabel('章节标题').nth(1).fill('后写的章节'); await page.getByLabel('章节正文').nth(1).fill('第二章正文')
    await page.getByRole('button', { name: '上移第2章' }).click()
    await page.getByRole('link', { name: '返回列表', exact: true }).click()
    await expect(page.getByText('还有未保存的修改。离开后，这些修改将丢失。')).toBeVisible()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page).toHaveURL(/\/admin\/editor\/\d+/); id = page.url().split('/').pop(); await saved(page)
    const item = await (await page.request.get(`/api/admin/contents/${id}`)).json()
    expect(item.details.chapters.map((c: any) => c.title)).toEqual(['后写的章节', '先写的章节'])
    await mutate(page.request, 'PUT', `/api/admin/contents/${id}`, { type: 'tutorial', slug: item.slug, title: '来自另一个页面的修改', summary: item.summary, bodyMarkdown: item.bodyMarkdown, lockVersion: item.lockVersion, details: item.details })
    await page.getByLabel('标题', { exact: true }).fill('过期页面的修改')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page.locator('.n-message-container')).toContainText(/冲突|刷新|修改/)
    expect((await (await page.request.get(`/api/admin/contents/${id}`)).json()).title).toBe('来自另一个页面的修改')
    await page.getByRole('link', { name: '返回列表', exact: true }).click(); await confirm(page)
    for (const path of ['dashboard', 'taxonomy', 'settings', 'logs', 'analytics', 'works', 'deals']) {
      await page.goto(`/admin/${path}`); await expect(page.locator('.admin-content h1')).toBeVisible()
    }
    await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/admin/dashboard')
    await page.getByRole('button', { name: '打开管理导航' }).click()
    await page.getByRole('navigation', { name: '管理导航' }).getByRole('link', { name: '媒体库' }).click()
    await expect(page.locator('.admin-content h1')).toHaveText('媒体库')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await expect(page.locator('.admin-sidebar')).not.toHaveClass(/open/)
    await page.screenshot({ path: 'test-results/admin-mobile.png', fullPage: true, animations: 'disabled' })
  } finally {
    if (id) { const current = await (await page.request.get(`/api/admin/contents/${id}`)).json(); await mutate(page.request, 'POST', `/api/admin/contents/${id}/trash`, { lockVersion: current.lockVersion }) }
  }
})

for (const type of ['work', 'deal']) test(`${type}: UI create, publish, reopen and update`, async ({ page, request }) => {
  await uiLogin(page)
  const slug = `${type}-ui-${Date.now()}`; let id: string | undefined
  try {
    await page.goto(`/admin/editor/new?type=${type}`)
    await page.getByLabel('标题', { exact: true }).fill(slug)
    await page.getByLabel('访问地址').fill(slug)
    await page.getByRole('textbox', { name: '正文', exact: true }).fill('真实页面发布验收正文')
    if (type === 'work') { await page.getByLabel('演示地址').fill('https://example.com/demo'); await page.getByLabel('技术栈（逗号分隔）').fill('Vue, Java'); await page.getByLabel('项目状态').selectOption('released') }
    if (type === 'deal') { await page.getByLabel('信息来源').fill('https://example.com/source'); await page.getByLabel('优惠或资源内容').fill('测试资源说明'); await page.getByLabel('适用条件与限制').fill('仅用于本地测试'); }
    await page.getByRole('button', { name: '发布', exact: true }).click(); await confirm(page)
    await expect(page).toHaveURL(/\/admin\/editor\/\d+/); id = page.url().split('/').pop()
    await expect(page.locator('.editor-topbar .state-badge')).toHaveText('已发布')
    expect((await request.get(`/api/public/contents/${slug}`)).status()).toBe(200)
    if (type === 'deal') { await page.getByRole('button', { name: '核验有效', exact: true }).click(); await confirm(page); await expect(page.locator('.n-message-container')).toContainText('核验结果已记录') }
    await page.reload()
    await page.getByRole('textbox', { name: '正文', exact: true }).fill('编辑后再发布的正文')
    await page.getByRole('button', { name: '更新发布', exact: true }).click(); await confirm(page)
    await expect.poll(async () => (await (await request.get(`/api/public/contents/${slug}`)).json()).bodyMarkdown).toBe('编辑后再发布的正文')
  } finally {
    if (id) { await login(request); const current = await (await request.get(`/api/admin/contents/${id}`)).json(); await mutate(request, 'POST', `/api/admin/contents/${id}/trash`, { lockVersion: current.lockVersion }) }
  }
})
