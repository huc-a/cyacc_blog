import { test, expect } from '@playwright/test'
import { credentials } from './helpers'

test('login shows a captcha on arrival and refreshes it after an incorrect answer', async ({ page }) => {
  let loginAttempts = 0, captchaIssues = 0
  const captchaImage = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNImNAAAALEAXGxfUYHAAAAAElFTkSuQmCC'
  await page.route('**/api/auth/login', async route => {
    loginAttempts++
    await route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({
        message: '请输入正确的图片验证码',
        captchaRequired: true,
        retryAfter: 0
      })
    })
  })
  await page.route('**/api/auth/captcha', route => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ image: `${captchaImage}#${++captchaIssues}`, expiresInSeconds: 120 })
  }))

  await page.goto('/admin/login')
  const captcha = page.getByLabel('图片验证码')
  const submit = page.getByRole('button', { name: '登录管理后台' })
  const image = page.getByRole('button', { name: '换一张验证码' }).locator('img')
  await expect(captcha).toBeVisible()
  await expect(page.getByText('两分钟内有效，点击图片可换一张。')).toHaveCount(0)
  await expect(image).toBeVisible()
  await expect.poll(async () => image.getAttribute('src')).toMatch(/^data:image\/png;base64,/)
  const firstImage = await image.getAttribute('src')

  await page.getByLabel('管理员账号').fill('captcha-ui-check')
  await page.getByLabel('密码', { exact: true }).fill('intentionally-wrong-password')
  await captcha.fill('00000')
  await submit.click()

  await expect.poll(() => loginAttempts).toBe(1)
  await expect(page.getByRole('alert')).toContainText('验证码')
  await expect(captcha).toBeVisible()
  await expect.poll(async () => image.getAttribute('src')).not.toBe(firstImage)
  const secondImage = await image.getAttribute('src')

  await page.getByRole('button', { name: '换一张验证码' }).click()
  await expect.poll(async () => image.getAttribute('src')).not.toBe(secondImage)
  expect(loginAttempts).toBe(1)
  expect(captchaIssues).toBeGreaterThanOrEqual(3)
})

test('real backend rejects a wrong captcha even when the password is correct', async ({ page }) => {
  const admin = credentials()
  await page.goto('/admin/login')
  await expect(page.getByLabel('图片验证码')).toBeVisible()
  await page.getByLabel('管理员账号').fill(admin.username)
  await page.getByLabel('密码', { exact: true }).fill(admin.password)
  await page.getByLabel('图片验证码').fill('00000')
  await page.getByRole('button', { name: '登录管理后台' }).click()
  await expect(page.getByRole('alert')).toContainText('请输入正确的图片验证码')
  await expect(page).toHaveURL(/\/admin\/login/)
})
