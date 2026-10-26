import { screen } from '@testing-library/react'
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
    await user.type(
      screen.getByRole('textbox', { name: 'Category (optional)' }),
      'Policy',
    )
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
      category: 'Policy',
      grants: [{ type: 'ROLE', role: 'STUDENT' }],
    })
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
