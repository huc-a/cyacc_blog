import { test, expect } from '@playwright/test'
import { login } from './helpers'

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
