import { expect, test } from '@playwright/test'

/** The test application's bootstrap admin (application-test.yaml); a test value, not a secret. */
const ADMIN = {
  email: 'admin@test.invalid',
  password: 'test-only-admin-password',
}

/**
 * Phase 9 on the main path: a question is asked, then the admin finds it in the audit log and in
 * the analytics, counted from the database, and opens the list of every document.
 */
test('a question shows up in the audit log and the analytics', async ({
  page,
}) => {
  await page.goto('/login')
  await page.getByLabel('Email').fill(ADMIN.email)
  await page.getByLabel('Password').fill(ADMIN.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible()

  // Nothing in the corpus is about the canteen, so the evidence gate answers without a model call.
  await page.getByRole('link', { name: 'Assistant' }).click()
  // The dashboard has a question box too; wait until the assistant's own is on screen.
  await expect(
    page.getByRole('heading', { name: 'Assistant', level: 1 }),
  ).toBeVisible()
  await page
    .getByRole('textbox', { name: 'Your question' })
    .fill('What is on the canteen menu on Fridays?')
  await page.getByRole('button', { name: 'Ask' }).click()
  await expect(
    page
      .getByRole('log', { name: 'Conversation' })
      .getByText('No answer found'),
  ).toBeVisible()

  await page.getByRole('link', { name: 'Audit log' }).click()
  await page.getByRole('button', { name: /Action/ }).click()
  await page.getByRole('option', { name: 'Question asked' }).click()
  const entry = page
    .getByRole('row')
    .filter({ hasText: 'Question asked' })
    .first()
  await expect(entry).toBeVisible()
  await expect(entry).toContainText('INSUFFICIENT_EVIDENCE')

  await page.getByRole('link', { name: 'Analytics' }).click()
  const questions = page.locator('dt', { hasText: /^Questions$/ }).locator('..')
  await expect(questions).toContainText('with no answer found')
  await expect(questions.locator('dd').first()).not.toHaveText('0')
  await expect(page.getByRole('heading', { name: 'System' })).toBeVisible()

  await page.getByRole('link', { name: 'All documents' }).click()
  await expect(
    page.getByRole('heading', { name: 'All documents' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Re-index all' })).toBeVisible()
})

/**
 * The sidebar hidden for reading room: its button stays in one place, pointing at it shows the
 * sidebar, and moving from the button onto the sidebar keeps it shown until the pointer leaves.
 */
test('the hidden sidebar shows under the pointer and stays while it is used', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('/login')
  await page.getByLabel('Email').fill(ADMIN.email)
  await page.getByLabel('Password').fill(ADMIN.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  const sidebar = page.getByRole('complementary', { name: 'Sidebar' })
  await expect(sidebar).toBeInViewport()

  const toggle = page.getByRole('button', { name: 'Hide sidebar' })
  const before = await toggle.boundingBox()
  await toggle.click()
  await page.mouse.move(800, 400)
  await expect(sidebar).not.toBeInViewport()

  const show = page.getByRole('button', { name: 'Keep sidebar open' })
  expect(await show.boundingBox()).toEqual(before)
  await show.hover()
  await expect(sidebar).toBeInViewport()
  await sidebar.getByRole('link', { name: 'Documents', exact: true }).hover()
  await page.waitForTimeout(400)
  await expect(sidebar).toBeInViewport()

  await page.mouse.move(800, 400)
  await expect(sidebar).not.toBeInViewport()

  await show.click()
  await page.mouse.move(800, 400)
  await expect(sidebar).toBeInViewport()
})
