#!/usr/bin/env node
/*
 * Demo data loader: demo accounts, four public course PDFs and their sharing, through the public
 * HTTP API of a running stack, so sign-in, upload, ingestion and the audit log all run as they do
 * for a real user.
 *
 *   node --env-file=.env scripts/demo-data.mjs [base-url] [pdf-folder]
 *
 * base-url defaults to http://localhost:3000 (the Compose stack), pdf-folder to test-files.
 * It signs in as the bootstrap admin (APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD),
 * registers the demo accounts and gives them their roles, then each faculty account uploads its
 * documents with their grants. Indexing needs the embedding model (Ollama) running.
 *
 * Meant for a fresh database. Run again with DEMO_PASSWORD set to the password it printed, and it
 * skips what already exists. Only public course documents are uploaded; the file holding student
 * data is refused by name.
 */
import { randomBytes } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'

const BASE = (process.argv[2] ?? 'http://localhost:3000').replace(/\/$/, '')
const FOLDER = process.argv[3] ?? 'test-files'
/** Holds student names and enrolment data: never uploaded. */
const FORBIDDEN = /tuesday_lab_batches/i
const INDEX_TIMEOUT_MS = 5 * 60 * 1000

const ACCOUNTS = {
  admin: { email: 'demo.admin@example.org', displayName: 'Demo Admin', role: 'ADMIN' },
  facultyA: { email: 'faculty.a@example.org', displayName: 'Faculty A', role: 'FACULTY' },
  facultyB: { email: 'faculty.b@example.org', displayName: 'Faculty B', role: 'FACULTY' },
  studentA: { email: 'student.a@example.org', displayName: 'Student A', role: 'STUDENT' },
  studentB: { email: 'student.b@example.org', displayName: 'Student B', role: 'STUDENT' },
}

/**
 * Who uploads what, and who may read it. The two students differ: each reads the lab exam
 * instructions (all students), and only one course document each.
 */
const DOCUMENTS = [
  {
    file: 'Lab exam instructions_students.pdf',
    title: 'Lab exam instructions',
    category: 'Examination',
    owner: 'facultyA',
    grants: [{ type: 'ROLE', role: 'STUDENT' }],
  },
  {
    file: 'Concisae Course Plan CSET109 OOP Java.pdf',
    title: 'Course plan CSET109 Object-Oriented Programming (Java)',
    category: 'Course plan',
    owner: 'facultyA',
    grants: [{ type: 'USER', user: 'studentA' }],
  },
  {
    file: 'Syllabus CSET305 High Performance Computing 2025-26.pdf',
    title: 'Syllabus CSET305 High Performance Computing 2025-26',
    category: 'Syllabus',
    owner: 'facultyB',
    grants: [{ type: 'USER', user: 'studentB' }],
  },
  {
    file: 'Project Evaluation Schedule.pdf',
    title: 'Project evaluation schedule',
    category: 'Schedule',
    owner: 'facultyB',
    grants: [{ type: 'ROLE', role: 'FACULTY' }],
  },
]

class ApiError extends Error {
  constructor(status, problem) {
    super(`${status} ${problem?.code ?? ''} ${problem?.detail ?? ''}`.trim())
    this.status = status
    this.problem = problem
  }
}

async function call(path, { token, method = 'GET', json, form } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let body
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(json)
  } else if (form) {
    body = form
  }
  const response = await fetch(`${BASE}${path}`, { method, headers, body })
  const text = await response.text()
  const data = text ? JSON.parse(text) : undefined
  if (!response.ok) throw new ApiError(response.status, data)
  return data
}

async function signIn(email, password) {
  const session = await call('/api/v1/auth/login', {
    method: 'POST',
    json: { email, password },
  })
  return session.accessToken
}

/** Stops the run with a message; the handler at the end prints it and sets the exit code. */
class Stop extends Error {}

function fail(message) {
  throw new Stop(message)
}

async function main() {
  const adminEmail = process.env.APP_BOOTSTRAP_ADMIN_EMAIL
  const adminPassword = process.env.APP_BOOTSTRAP_ADMIN_PASSWORD
  if (!adminEmail || !adminPassword) {
    fail(
      'APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD are not set. Run with --env-file=.env.',
    )
  }
  for (const document of DOCUMENTS) {
    if (FORBIDDEN.test(document.file)) fail(`Refusing ${document.file}: it holds student data.`)
  }

  const meta = await call('/api/v1/meta').catch((error) =>
    fail(`No backend answers at ${BASE} (${error.message}). Is the stack running?`),
  )
  console.log(`${meta.name} ${meta.version} at ${BASE}`)

  const given = process.env.DEMO_PASSWORD
  const password = given ?? `Pramaan-demo-${randomBytes(4).toString('hex')}`
  const adminToken = await signIn(adminEmail, adminPassword).catch((error) =>
    fail(`The bootstrap admin could not sign in: ${error.message}`),
  )

  console.log('\nAccounts')
  const ids = {}
  for (const [key, account] of Object.entries(ACCOUNTS)) {
    let id
    try {
      const created = await call('/api/v1/auth/register', {
        method: 'POST',
        json: { email: account.email, displayName: account.displayName, password },
      })
      id = created.id
      console.log(`  created  ${account.email}`)
    } catch (error) {
      if (error.problem?.code !== 'EMAIL_TAKEN') {
        fail(`Registering ${account.email} failed: ${error.message}`)
      }
      if (!given) {
        fail(
          `${account.email} already exists. Set DEMO_PASSWORD to the password printed by the first run, or start from a fresh database.`,
        )
      }
      const found = await call(
        `/api/v1/admin/users?query=${encodeURIComponent(account.email)}&size=5`,
        { token: adminToken },
      )
      id = found.items.find((user) => user.email === account.email)?.id
      if (!id) fail(`${account.email} exists but the admin cannot find it.`)
      console.log(`  exists   ${account.email}`)
    }
    if (account.role !== 'STUDENT') {
      await call(`/api/v1/admin/users/${id}`, {
        token: adminToken,
        method: 'PATCH',
        json: { role: account.role },
      })
    }
    ids[key] = id
  }

  console.log('\nDocuments')
  const tokens = {}
  const uploaded = []
  for (const document of DOCUMENTS) {
    tokens[document.owner] ??= await signIn(ACCOUNTS[document.owner].email, password)
    const token = tokens[document.owner]
    const grants = document.grants.map((grant) =>
      grant.type === 'ROLE'
        ? { type: 'ROLE', role: grant.role }
        : { type: 'USER', userId: ids[grant.user] },
    )
    const bytes = await readFile(join(FOLDER, document.file)).catch(() =>
      fail(`${join(FOLDER, document.file)} was not found. Pass the folder of the course PDFs.`),
    )
    const form = new FormData()
    form.append('file', new Blob([bytes], { type: 'application/pdf' }), document.file)
    form.append(
      'metadata',
      new Blob(
        [JSON.stringify({ title: document.title, category: document.category, grants })],
        { type: 'application/json' },
      ),
    )
    let id
    try {
      id = (await call('/api/v1/documents', { token, method: 'POST', form })).id
      console.log(`  uploaded ${document.title}`)
    } catch (error) {
      if (error.problem?.code !== 'DUPLICATE_DOCUMENT') {
        fail(`Uploading ${document.file} failed: ${error.message}`)
      }
      id = error.problem.documentId
      // Already there: make its sharing match this list again.
      await call(`/api/v1/documents/${id}/grants`, {
        token,
        method: 'PUT',
        json: { grants },
      })
      console.log(`  exists   ${document.title}`)
    }
    uploaded.push({ ...document, id, token })
  }

  console.log('\nIndexing (needs Ollama)')
  const deadline = Date.now() + INDEX_TIMEOUT_MS
  let failed = 0
  for (const document of uploaded) {
    let status
    for (;;) {
      const current = await call(`/api/v1/documents/${document.id}`, { token: document.token })
      status = current.status
      if (status === 'INDEXED') {
        console.log(`  ready    ${document.title} (${current.chunkCount} ${current.chunkCount === 1 ? 'passage' : 'passages'})`)
        break
      }
      if (status === 'FAILED') {
        console.log(`  FAILED   ${document.title}: ${current.failureMessage ?? 'no reason'}`)
        failed += 1
        break
      }
      if (Date.now() > deadline) {
        console.log(`  waiting  ${document.title} is still ${status}; check Ollama`)
        failed += 1
        break
      }
      await new Promise((resolve) => setTimeout(resolve, 2000))
    }
  }

  console.log(`\nSign in at ${BASE} with any of these. Password for all: ${password}`)
  const width = Math.max(...Object.values(ACCOUNTS).map((a) => a.email.length))
  for (const account of Object.values(ACCOUNTS)) {
    console.log(`  ${account.email.padEnd(width)}  ${account.role.padEnd(7)}  ${account.displayName}`)
  }
  console.log('\nWho reads what')
  console.log('  Student A: Lab exam instructions, Course plan CSET109')
  console.log('  Student B: Lab exam instructions, Syllabus CSET305')
  console.log('  Faculty A and B: their own uploads and the Project evaluation schedule')
  console.log('  Admins: everything')
  if (failed) fail(`${failed} document(s) are not ready.`)
}

// No process.exit: on Windows it can abort Node while a connection is still closing.
main().catch((error) => {
  console.error(`\n${error instanceof Stop ? error.message : (error.stack ?? error)}`)
  process.exitCode = 1
})
