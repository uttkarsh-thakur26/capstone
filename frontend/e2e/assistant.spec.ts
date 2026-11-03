import { expect, test } from '@playwright/test'
import { pdfWithLines } from './pdf.ts'

/** The test application's bootstrap admin (application-test.yaml); a test value, not a secret. */
const ADMIN = {
  email: 'admin@test.invalid',
  password: 'test-only-admin-password',
}

const RULE = 'Hostel gates close at 22:30 for every resident student.'

/**
 * The main path of 16.4: sign in, upload, wait until the document is indexed, ask, open a
 * citation. The answer comes from the fake chat model, which cites source 1.
 */
test('sign in, upload, wait until indexed, ask and open a citation', async ({
  page,
}) => {
  const title = `Hostel rules ${Date.now()}`

  await page.goto('/login')
  await page.getByLabel('Email').fill(ADMIN.email)
  await page.getByLabel('Password').fill(ADMIN.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible()

  await page.goto('/documents/upload')
  await page.locator('input[type="file"]').setInputFiles({
    name: 'hostel-rules.pdf',
    mimeType: 'application/pdf',
    buffer: pdfWithLines([RULE, `Reference ${title}.`]),
  })
  await page.getByLabel('Title').fill(title)
  await page.getByRole('button', { name: 'Upload', exact: true }).click()

  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expect(page.getByText('Ready', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Assistant' }).click()
  await page
    .getByRole('textbox', { name: 'Your question' })
    .fill('When do hostel gates close for every resident student?')
  await page.getByRole('button', { name: 'Ask' }).click()

  const log = page.getByRole('log', { name: 'Conversation' })
  await expect(log.getByText('The sources say so')).toBeVisible()
  const chip = log
    .getByRole('button', { name: `Source 1: ${title}, page 1` })
    .first()
  await chip.click()

  const panel = page.getByRole('dialog', { name: `Source 1: ${title}` })
  await expect(panel.getByText(RULE)).toBeVisible()
  await expect(panel.getByTitle(`${title}, page 1`)).toHaveAttribute(
    'src',
    /^blob:.*#page=1$/,
  )
})
