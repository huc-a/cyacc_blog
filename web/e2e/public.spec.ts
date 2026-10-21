import { test, expect } from '@playwright/test'
import { login, mutate } from './helpers'
const token = `browser-${Date.now()}`
let article: any, tutorial: any
test.beforeAll(async ({ request }) => {
  await login(request)
  article = await mutate(request, 'POST', '/api/admin/contents', { type: 'article', slug: token, title: `阅读验证 ${token}`, summary: '真实接口的前台浏览器测试', bodyMarkdown: '# 文字的节奏\n\n这是公开正文。\n\n<script>window.markdownExecuted=true</script>\n\n```js\nconsole.log("hello")\n```', lockVersion: 0 })
  article = await mutate(request, 'POST', `/api/admin/contents/${article.id}/publish`, { lockVersion: article.lockVersion })
  tutorial = await mutate(request, 'POST', '/api/admin/contents', { type: 'tutorial', slug: `${token}-tutorial`, title: `教程验证 ${token}`, summary: '完整章节流程', bodyMarkdown: '# 教程介绍', lockVersion: 0, details: { chapters: [{ chapterKey: crypto.randomUUID(), slug: 'start', title: '从这里开始', bodyMarkdown: '# 第一步\n\n章节一正文' }, { chapterKey: crypto.randomUUID(), slug: 'next', title: '继续探索', bodyMarkdown: '# 第二步\n\n章节二正文' }] } })
  tutorial = await mutate(request, 'POST', `/api/admin/contents/${tutorial.id}/publish`, { lockVersion: tutorial.lockVersion })
})
test.afterAll(async ({ request }) => {
  await login(request)
  for (const item of [article, tutorial]) if (item) { const current = await (await request.get(`/api/admin/contents/${item.id}`)).json(); await mutate(request, 'POST', `/api/admin/contents/${item.id}/trash`, { lockVersion: current.lockVersion }) }
})
test('home, search, SSR reading and safe Markdown work with real content', async ({ page, request }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message))
  const ssr = await request.get(`/articles/${token}`); expect(ssr.status()).toBe(200); expect(await ssr.text()).toContain(`阅读验证 ${token}`)
  const site = await (await request.get('/api/public/site')).json()
  await page.goto('/'); await expect(page.getByRole('heading', { level: 1 })).toHaveText(site.headline)
  await page.getByRole('link', { name: '搜索', exact: true }).click()
  await page.getByRole('searchbox', { name: '搜索内容' }).fill(token)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await page.getByRole('link', { name: `阅读验证 ${token}`, exact: true }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(`阅读验证 ${token}`)
  await expect(page.locator('.prose')).toContainText('这是公开正文')
  expect(await page.evaluate(() => (window as any).markdownExecuted)).toBeUndefined()
  await page.getByRole('button', { name: '专注模式', exact: true }).click(); await expect(page.locator('.reading')).toHaveClass(/focused/)
  await page.getByRole('button', { name: '增大字号' }).click(); await expect(page.locator('.reading-paper')).toHaveAttribute('style', /20px/)
  await page.screenshot({ path: 'test-results/reader-desktop.png', fullPage: true })
  expect(errors).toEqual([])
})
test('tutorial chapters, missing content, and mobile navigation', async ({ page }) => {
  await page.goto(`/tutorials/${token}-tutorial`)
  await page.getByRole('link', { name: /01 从这里开始/ }).click(); await expect(page.locator('.prose').first()).toContainText('章节一正文')
  await page.getByRole('link', { name: '下一章 →' }).click(); await expect(page.locator('.prose').first()).toContainText('章节二正文')
  const missing = await page.goto('/articles/no-such-public-page'); expect(missing?.status()).toBe(404)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('这一页')
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/')
  await page.getByRole('button', { name: '打开导航' }).click(); await page.getByRole('navigation', { name: '移动导航' }).getByRole('link', { name: '网站', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('收藏值得')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: 'test-results/mobile-collection.png', fullPage: true })
})
