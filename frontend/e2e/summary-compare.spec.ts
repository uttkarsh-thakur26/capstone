import { expect, test, type Page } from '@playwright/test'
import { pdfWithLines } from './pdf.ts'

/** The test application's bootstrap admin (application-test.yaml); a test value, not a secret. */
const ADMIN = {
  email: 'admin@test.invalid',
  password: 'test-only-admin-password',
}

/** Uploads a one-page PDF with these lines and waits until it is indexed; returns its id. */
async function upload(page: Page, title: string, lines: string[]) {
  await page.goto('/documents/upload')
  await page.locator('input[type="file"]').setInputFiles({
    name: 'notice.pdf',
    mimeType: 'application/pdf',
    buffer: pdfWithLines(lines),
  })
  await page.getByLabel('Title').fill(title)
  await page.getByRole('button', { name: 'Upload', exact: true }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expect(page.getByText('Ready', { exact: true })).toBeVisible()
  return new URL(page.url()).pathname.split('/').pop() as string
}

/**
 * Phase 8 on the main path: summarize a document and open a cited page, then compare two versions
 * and see the difference with its page on each side. The fake chat model writes the summary from
 * the first passage and quotes the first words of each version.
 */
test('summarize a document and compare two versions', async ({ page }) => {
  const stamp = Date.now()
  const older = `Lab notice ${stamp} A`
  const newer = `Lab notice ${stamp} B`

  await page.goto('/login')
  await page.getByLabel('Email').fill(ADMIN.email)
  await page.getByLabel('Password').fill(ADMIN.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible()

  const olderId = await upload(page, older, [
    `Earlier version ${stamp}: the lab exam lasts 55 minutes.`,
    'Phones must be switched off.',
  ])

  await page.getByRole('button', { name: 'Summarize' }).click()
  await expect(page.getByText('The document sets out its rules')).toBeVisible()
  await page
    .getByRole('button', { name: `Source 1: ${older}, page 1` })
    .first()
    .click()
  const panel = page.getByRole('dialog', { name: `Source 1: ${older}` })
  await expect(panel.getByText(/the lab exam lasts 55 minutes/)).toBeVisible()
  await panel.getByRole('button', { name: 'Close' }).click()

  const newerId = await upload(page, newer, [
    `Later version ${stamp}: the lab exam lasts 60 minutes.`,
    'Phones must be switched off and kept in bags.',
  ])

  await page.goto(`/compare?left=${olderId}&right=${newerId}`)
  await page.getByRole('button', { name: 'Compare' }).click()
  await expect(page.getByText('1 difference found.')).toBeVisible()
  await expect(
    page.getByText(`Earlier version ${stamp}:`, { exact: false }).first(),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: `Open ${older}, page 1` }),
  ).toBeVisible()
})
