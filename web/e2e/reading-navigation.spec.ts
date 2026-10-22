import { test, expect } from '@playwright/test'

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
