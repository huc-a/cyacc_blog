import { test, expect, type APIRequestContext } from '@playwright/test'
import { login, mutate } from './helpers'

test('wider home sidebar leaves room for the feed at desktop and mobile sizes', async ({ page }) => {
  for (const width of [1440, 1104, 951, 851, 850, 390]) {
    await page.setViewportSize({ width, height: 828 })
    await page.goto('/')
    const sidebar = await page.locator('.left-sidebar').boundingBox()
    const feed = await page.locator('.feed-column').boundingBox()
    if (width > 850) {
      expect(sidebar!.width).toBe(width > 1150 ? 280 : 260)
      expect(feed!.width).toBeGreaterThan(450)
      expect(sidebar!.x + sidebar!.width).toBeLessThan(feed!.x)
    } else {
      expect(sidebar!.y + sidebar!.height).toBeLessThan(feed!.y)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    if ([1440, 951, 390].includes(width)) {
      await page.locator('.profile').scrollIntoViewIfNeeded()
      await page.screenshot({ path: `test-results/wider-sidebar-${width}.png`, animations: 'disabled' })
    }
  }
})

test.describe('reading neighbors', () => {
  const token = `neighbors-${Date.now()}`
  const articles: any[] = []
  let fixtureRequest: APIRequestContext
  test.beforeAll(async ({ playwright, baseURL }) => {
    fixtureRequest = await playwright.request.newContext({ baseURL })
    await login(fixtureRequest)
    for (const title of ['从一次尝试开始', '在文字之间继续探索', '把新的发现留给明天']) {
      const draft = await mutate(fixtureRequest, 'POST', '/api/admin/contents', { type: 'article', slug: `${token}-${articles.length}`, title, summary: '阅读导航测试', bodyMarkdown: `这篇文章的正文：${title}`, lockVersion: 0 })
      articles.push(draft)
      articles[articles.length - 1] = await mutate(fixtureRequest, 'POST', `/api/admin/contents/${draft.id}/publish`, { lockVersion: draft.lockVersion })
    }
  })
  test.afterAll(async () => {
    if (!fixtureRequest) return
    try {
      for (const item of articles) {
        const current = await (await fixtureRequest.get(`/api/admin/contents/${item.id}`)).json()
        await mutate(fixtureRequest, 'POST', `/api/admin/contents/${item.id}/trash`, { lockVersion: current.lockVersion })
      }
    } finally { await fixtureRequest.dispose() }
  })
  test('article neighbors render in SSR and navigate both ways on desktop and mobile', async ({ page, request }) => {
    const errors: string[] = []; page.on('pageerror', error => errors.push(error.message))
    const [older, middle, newer] = articles
    const ssr = await request.get(`/articles/${middle.slug}`)
    expect(ssr.status()).toBe(200)
    const html = await ssr.text()
    expect(html).toContain(`/articles/${older.slug}`)
    expect(html).toContain(`/articles/${newer.slug}`)
    const navigation = page.getByRole('navigation', { name: '同栏目阅读导航' })
    for (const width of [1104, 390]) {
      await page.setViewportSize({ width, height: 828 })
      await page.goto(`/articles/${middle.slug}`)
      await expect(navigation.getByRole('link', { name: `← 上一篇 ${older.title}` })).toHaveAttribute('href', `/articles/${older.slug}`)
      await expect(navigation.getByRole('link', { name: `下一篇 → ${newer.title}` })).toHaveAttribute('href', `/articles/${newer.slug}`)
      await navigation.scrollIntoViewIfNeeded()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
      await page.screenshot({ path: `test-results/reading-neighbors-${width}.png`, animations: 'disabled' })
      await navigation.getByRole('link', { name: /上一篇/ }).click()
      await expect(page).toHaveURL(`/articles/${older.slug}`)
      await expect(page.locator('.prose')).toContainText(older.title)
      await navigation.getByRole('link', { name: /下一篇/ }).focus(); await page.keyboard.press('Enter')
      await expect(page).toHaveURL(`/articles/${middle.slug}`)
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(middle.title)
      await navigation.getByRole('link', { name: /下一篇/ }).click()
      await expect(page).toHaveURL(`/articles/${newer.slug}`)
      await expect(page.locator('.prose')).toContainText(newer.title)
      await expect(navigation.locator('a[rel="next"]')).toHaveCount(0)
      await expect(navigation).toContainText('已是最新一篇')
      await page.goBack()
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(middle.title)
    }
    expect(errors).toEqual([])
  })
})
