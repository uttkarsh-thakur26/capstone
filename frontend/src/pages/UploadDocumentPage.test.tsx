import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { DOC_ID, documentDetail } from '../test/documents'
import { fakeApi, fakeUploads, json, META, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const LIMITS = { maxFileSizeBytes: 1000, contentTypes: ['application/pdf'] }

function facultyApi(role: 'FACULTY' | 'STUDENT' = 'FACULTY') {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session(role)),
    'GET /api/v1/documents/upload-limits': () => json(LIMITS),
    [`GET /api/v1/documents/${DOC_ID}`]: () => json(documentDetail()),
    [`GET /api/v1/documents/${DOC_ID}/grants`]: () => json({ grants: [] }),
  })
}

function pdf(name = 'exam-policy.pdf', size = 500): File {
  return new File([new Uint8Array(size)], name, { type: 'application/pdf' })
}

/** React Aria's FileTrigger keeps the native file input hidden. */
function fileInput(): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')
  if (!input) throw new Error('No file input')
  return input
}

async function openUpload() {
  const app = renderApp('/documents/upload')
  await screen.findByRole('heading', { name: 'Upload a document' })
  await screen.findByText(/up to 1000 bytes/)
  return app
}

describe('UploadDocumentPage', () => {
  it('refuses a file that is not a PDF before sending anything', async () => {
    facultyApi()
    const uploads = fakeUploads(() => json({}))
    await openUpload()
    // The picker's accept filter is bypassed, as a user can do by choosing "All files".
    const user = userEvent.setup({ applyAccept: false })

    await user.upload(
      fileInput(),
      new File(['hello'], 'notes.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByText('Choose a PDF file.')).toBeVisible()
    expect(uploads).toHaveLength(0)
  })

  it("refuses a file above the server's limit", async () => {
    facultyApi()
    const uploads = fakeUploads(() => json({}))
    const { user } = await openUpload()

    await user.upload(fileInput(), pdf('big.pdf', 2000))

    expect(
      await screen.findByText('The file is 2 KB; the limit is 1000 bytes.'),
    ).toBeVisible()
    expect(uploads).toHaveLength(0)
  })

  it('sends the file with its details and sharing, shows progress and opens the document', async () => {
    facultyApi()
    let finish: (response: Response) => void = () => {}
    const uploads = fakeUploads(
      () => new Promise<Response>((resolve) => (finish = resolve)),
    )
    const { user, router } = await openUpload()

    await user.upload(fileInput(), pdf())
    // The title is taken from the file name and can be changed.
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue(
      'exam-policy',
    )
    await user.clear(screen.getByRole('textbox', { name: 'Title' }))
    await user.type(
      screen.getByRole('textbox', { name: 'Title' }),
      'Examination Policy 2026',
    )
    await user.click(screen.getByRole('button', { name: /Category/ }))
    await user.click(await screen.findByRole('option', { name: 'Policy' }))
    await user.click(screen.getByRole('checkbox', { name: 'All students' }))
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    const progress = await screen.findByRole('progressbar', {
      name: 'Uploading',
    })
    expect(progress).toHaveAttribute('aria-valuenow', '50')
    finish(json(documentDetail(), 201))

    expect(
      await screen.findByRole('heading', { name: 'Examination Policy 2026' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe(`/documents/${DOC_ID}`)
    expect(screen.getByText(/The document was uploaded/)).toBeVisible()

    expect(uploads).toHaveLength(1)
    expect(uploads[0].path).toBe('/api/v1/documents')
    expect(uploads[0].headers.Authorization).toBe('Bearer token-FACULTY')
    expect((uploads[0].form.get('file') as File).name).toBe('exam-policy.pdf')
    const metadata = uploads[0].form.get('metadata') as Blob
    expect(metadata.type).toBe('application/json')
    expect(JSON.parse(await metadata.text())).toEqual({
      title: 'Examination Policy 2026',
      category: 'POLICY',
      grants: [{ type: 'ROLE', role: 'STUDENT' }],
    })
  })

  it('shows each rule broken next to its field, keeps the text and keeps Upload off until it is fixed', async () => {
    facultyApi()
    const uploads = fakeUploads(() => json(documentDetail(), 201))
    const { user } = await openUpload()
    const upload = screen.getByRole('button', { name: 'Upload' })
    expect(upload).toBeDisabled()
    await user.upload(fileInput(), pdf())
    const title = screen.getByRole('textbox', { name: 'Title' })

    for (const [typed, message] of [
      ['@@@###', 'Use at least 3 letters, not only digits or symbols.'],
      ['1111', 'Use at least 3 letters, not only digits or symbols.'],
      ['ab', 'Use 3 to 200 characters.'],
      ['a'.repeat(201), 'Use 3 to 200 characters.'],
      ['   ', 'Enter a title.'],
    ]) {
      await user.clear(title)
      await user.type(title, typed)
      await user.tab()
      expect(await screen.findByText(message)).toBeVisible()
      expect(title).toHaveAttribute('aria-invalid', 'true')
      expect(title).toHaveValue(typed)
      expect(upload).toBeDisabled()
    }

    const description = screen.getByRole('textbox', {
      name: 'Description (optional)',
    })
    await user.clear(title)
    await user.type(title, '  Lab   exam rules ')
    await user.click(description)
    await user.paste('d'.repeat(2001))
    await user.tab()
    expect(
      await screen.findByText('Use at most 2,000 characters.'),
    ).toBeVisible()
    expect(upload).toBeDisabled()
    await user.clear(description)
    await user.type(description, '  Rules for the lab exam.  ')

    const date = screen.getByLabelText('Effective date (optional)')
    expect(date).toHaveAttribute('min', '2000-01-01')
    await user.type(date, '1999-12-31')
    await user.tab()
    expect(
      await screen.findByText(/^Pick a date from .*2000 to /),
    ).toBeVisible()
    expect(date).toHaveValue('1999-12-31')
    expect(upload).toBeDisabled()
    await user.clear(date)
    await user.type(date, '2026-07-01')
    await user.tab()

    await waitFor(() => expect(upload).toBeEnabled())
    expect(screen.queryByText(/Pick a date/)).not.toBeInTheDocument()
    await user.click(upload)
    await waitFor(() => expect(uploads).toHaveLength(1))
    const metadata = uploads[0].form.get('metadata') as Blob
    expect(JSON.parse(await metadata.text())).toEqual({
      title: 'Lab exam rules',
      description: 'Rules for the lab exam.',
      documentDate: '2026-07-01',
      grants: [],
    })
  })

  it('puts a field error from the server next to its field and keeps what was typed', async () => {
    facultyApi()
    fakeUploads(() =>
      json(
        {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'The request is not valid.',
          code: 'VALIDATION_FAILED',
          errors: [
            {
              field: 'documentDate',
              message: 'must be between 2000-01-01 and 2027-11-09',
            },
          ],
        },
        400,
      ),
    )
    const { user } = await openUpload()
    await user.upload(fileInput(), pdf())
    const date = screen.getByLabelText('Effective date (optional)')
    await user.type(date, '2026-07-01')
    await user.tab()

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Upload' })).toBeEnabled(),
    )
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    expect(
      await screen.findByText('must be between 2000-01-01 and 2027-11-09'),
    ).toBeVisible()
    expect(date).toHaveAttribute('aria-invalid', 'true')
    expect(date).toHaveValue('2026-07-01')
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue(
      'exam-policy',
    )
  })

  it('shows the server refusing a duplicate, with a link to the existing document', async () => {
    facultyApi()
    fakeUploads(() =>
      json(
        {
          type: 'about:blank',
          title: 'Conflict',
          status: 409,
          detail: 'You have already uploaded this file.',
          code: 'DUPLICATE_DOCUMENT',
          documentId: DOC_ID,
        },
        409,
      ),
    )
    const { user } = await openUpload()

    await user.upload(fileInput(), pdf())
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    expect(
      await screen.findByText('You have already uploaded this file.'),
    ).toBeVisible()
    expect(
      screen.getByRole('link', { name: 'Open the existing document' }),
    ).toHaveAttribute('href', `/documents/${DOC_ID}`)
  })

  it('is not offered to students', async () => {
    facultyApi('STUDENT')

    renderApp('/documents/upload')

    expect(
      await screen.findByRole('heading', { name: /not allowed/i }),
    ).toBeVisible()
  })
})
