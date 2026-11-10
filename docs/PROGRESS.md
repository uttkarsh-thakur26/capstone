# Progress

Updated 2026-11-09. Format: specification Section 24.8.

## Current phase

**Phase 9, administration, audit and analytics, is built on `feat/phase-9-admin`**, with the rate limits and the Section 17 review that the plan put in Phase 10. Waiting for the owner's click-through on kp-demo, then review and merge. Decisions: ADR-041 (D14 off, with no column for question text).

- Admin screens: All documents (status, failure reason, reprocess, delete, re-index all), Audit log (filters by who, action, outcome and days), Analytics (questions per day by outcome, AI cost per day from the logged tokens, uploads, queries by type, response times, documents by status, failures, users by role, most cited documents, system), and Set password on Users.
- Every action of 14.2 is recorded; `PASSWORD_RESET` and `REINDEX_STARTED` are new, and a failed comparison is now recorded too. `APP_EMBEDDING_REINDEX_ON_MODEL_CHANGE` lets startup re-index after a model change (S11).
- Rate limits, all settings in `.env.example`: AI requests 10 per minute and 200 per day per user, failed sign-ins 5 per email from one address and 20 per address in 15 minutes, registrations 50 per address per hour; a 429 counts down on screen.
- Owner's click-through on kp-demo: long dropdown lists now scroll, a long chosen option no longer makes its box taller, and the sidebar can be hidden for reading room (pointing at its button shows it for a moment, the button keeps it open; the choice is kept in the browser).
- Section 17 review: fixed SEC-6 (no CSP, `nosniff` or frame-ancestors on the page), AUD-2 (a password typed as the sign-in email was stored in the audit log), SEC-8 (no limits; `sort` without a maximum length), SEC-14 (no `npm audit`; it found `zod` 4.6.5, moved to 4.6.7). The other items were checked and needed nothing (ADR-041).
- Checks (2026-11-09): `./mvnw verify` 890 tests and the format check; frontend 162 tests, lint, type check, build, format; Playwright 4 tests. Mutation checks failed the tests as expected: the question text in the `AI_QUERY` entry (`AuditCoverageTest`), the ask endpoint without its rate limit (`RateLimitTest`, 3 of 6).

**Phase 8, hybrid search, summaries and comparison, is done**, merged through pull request [#10](https://github.com/uttkarsh-thakur26/capstone/pull/10) (merge commit `3a92d67`) with every CI check green. Decisions: ADR-040 (S22, S23 resolved). Phase 7, the first evaluation run, is still to do.

- Hybrid search ("Best match", the new default) fuses the semantic and keyword rankings with Reciprocal Rank Fusion in one SQL statement; the assistant retrieves with it; the evidence gate still compares cosine similarity.
- Summaries: background run, one pass up to 4,000 tokens, map-reduce above, at most 8 model calls, more than 40 pages refused; every item cites its pages and is checked in code; stored per document and served only after a fresh access check while the document is indexed as it was.
- Comparison: chunks paired by mutual closest similarity, identical passages dropped, one model call, every difference checked against both documents' text, pages on both sides.
- Upload and edit forms: title, description, category (fixed list, migration V11) and date rules on the server and in the forms; a field of the wrong kind answers 400 naming it; times shown with their zone.
- Checks (2026-11-08): `./mvnw verify` 848 tests and the format check, all passed on an idle machine; one earlier run with the frontend tests beside it failed `SearchAfterIngestionTest` once (semantic mode, page 3 instead of 2), which passed 3 of 3 alone (see Known issues). Frontend 148 tests, lint, type check, build, format; Playwright 2 tests (54.5 s, the new one summarizes and compares). Mutation checks failed the tests as expected: no access check in the hybrid keyword ranking (38 of 180 matrix cases), no fresh access check on a stored summary (11), no quote check in comparison, no number check in summaries.
- Reviews (ADR-028): the owner's click-through on kp-demo found the embedding model stopped (Ollama, not the code) and that best match failed with search by meaning's message (fixed in `87d997a`, test first). The independent review (two fresh-context reviewers) found nothing in access control or in what reaches the model, and six defects in the screens, each fixed test first where a test can show it: a cancelled edit and its errors coming back (`3f9191e`), a summary card asking every 2 s forever after a failed read and showing a stale failure during a retry (`d7f316a`), a latest effective date one day later than the server's after 29 February (`ade1feb`), a long file name scrolling the upload page sideways at 375 px (`f26c4a4`), and Compare enabled for a document the pickers do not list (`251d97f`). One reported point was not a defect: a best-match result always has a similarity.
- Live (kp-demo, `gpt-6-luna`, public course PDFs; the CSET203 PDF is readable by Faculty A and students, so questions were scoped to one document and no call read it). 6 calls, $0.0020 together:

| Check | Result | Calls, tokens in / out | Cost |
|---|---|---|---|
| "module 1 java" | Best match: Module 1 passage first (a tie with Module 3, broken by keyword order); Meaning: Module 3 first; Keywords: Module 1 first | 0 (local embeddings) | $0 |
| Ask "How long is the lab exam?", scoped | "The lab exam will be of 55 minutes duration. [1]"; off-topic question gated at similarity 0.172 | 1, 948 / 35 | $0.000112 |
| Summary of the owner's 93-page HR manual, default limits | Refused before any call: "This document has 93 pages. Summaries are limited to 40 pages." | 0 | $0 |
| Summary of the CSET109 course plan, limits lowered for the demo (single pass 800, groups 700) | Map-reduce, 2 groups, 9 key points and 1 date with pages; Student A reads it, Student B gets 404 | 3, 2,918 / 841 | $0.000712 |
| Compare CSET109 with CSET305 (as Demo Admin, the only account that reads both) | First with a 0.5 floor: no pair, 2 one-sided differences; after mutual-closest pairing: 9 differences, 6 with pages on both sides (credits 6 against 4, Module 1 14 against 18 hours), 2 discarded by the checks | 1 + 1, 2,829 / 584 and 2,802 / 654 | $0.000575 + $0.000607 |

**The design pass and demo data (between Phases 6 and 7) are done, through pull request [#9](https://github.com/uttkarsh-thakur26/capstone/pull/9).** Decisions: ADR-039 (D1 is Pramaan). Next: Phase 7, the first evaluation run.

- One look on every screen: colour tokens with a light and a dark set, a System / Light / Dark switch, shared page header, cards, loading, error and empty states; a sidebar from 1024 px, a menu drawer below; no page scrolls sideways at 375 px (checked on all routes in Chromium).
- Dashboard at `/`: quick ask, five newest readable documents, five latest conversations, an administration card for admins.
- Every page is a lazy route. JavaScript loaded on a first visit, before → after: every page 888.1 kB (254.6 kB gzip) → sign-in 613.3 kB (195.4 kB), dashboard 505.3 kB (164.1 kB), documents 584.9 kB (191.0 kB), upload 835.6 kB (252.1 kB); the largest file is 257.8 kB, so Vite no longer warns.
- Chat redesign after the owner's click-through: question box fixed at the bottom, "Jump to latest", answers as plain text in a 720 px column through a safe Markdown subset (no HTML), repeated markers shown once, one source chip per document and pages, the citation panel sliding over the chat, thinking dots; prompt `answer-v3` (one-line answer first, lists only for several items, one marker per paragraph or list; spec 11.3 rule 3 corrected, ADR-039).
- `scripts/demo-data.mjs`: demo accounts, four public course PDFs and their sharing through the API (README, "Demo data").
- Flaky frontend run (16 of 17 files once): not reproduced in 5 runs before the change; a test needing 1.17 s against the 1 s wait of `findBy` failed 1 run in 3 after the routes became lazy. Page modules now load once in the test setup and the wait is 3 s (ADR-039); 8 runs in a row then passed.
- Checks (2026-11-03, after the review fixes): `./mvnw verify` 613 tests and the format check (one earlier run under load failed 2, see Known issues); frontend 125 tests (new: dashboard, role navigation, menu drawer, theme, answer Markdown, merged sources, the page-load error screen), lint, type check, build, format; Playwright passed (39.0 s).

**Phase 6, conversations and the citation viewer, is done**, merged through pull request [#8](https://github.com/uttkarsh-thakur26/capstone/pull/8) with every CI check green. The MVP of specification 23 is complete. Decisions: ADR-038 (S19, S20, the viewer, follow-ups). Next: Phase 7, the first evaluation run.

Evidence (2026-11-02):

- `./mvnw verify`: 612 backend tests and the format check. The 19.2 "Conversations" tests: `ConversationsTest` (another user's id gives 404 on all five endpoints, admins included; delete removes messages and citations; a follow-up is rewritten into a standalone query; a revoked grant and a deleted document leave the context and show as unavailable, the snapshot kept). Mutation checks failed the tests as expected: no history filter, the rewrite not used, citations always available; in the frontend, answer text rendered as HTML, and unavailable citations shown as chips.
- Frontend: 102 tests, lint, type check, build, format. Playwright (`npx playwright test`): sign in, upload, wait until indexed, ask, open the citation, against the test application with the fake models; it passed locally in 51 s and runs in CI as a fourth job.
- Demo steps 1 to 9 of Appendix B, in the browser pane (Chromium) on a throwaway Compose stack (`prod` profile, real `gpt-6-luna` and `qwen3-embedding:0.6b`, generated test accounts, no other documents). Stand-ins under their real titles: "Lab exam instructions" shared with all students for the examination policy, "Project Evaluation Schedule" shared with all faculty for the faculty guidelines. The upload form got its file from a script, because the pane cannot drive the system file picker.

| Step | What happened |
|---|---|
| 1 | The admin promoted "Demo Faculty" to FACULTY on the Users page |
| 2, 3 | The faculty member uploaded both documents with their grants; each went from Processing to Ready on screen |
| 4 | Student: "How long is the lab exam?" gave "The lab exam will be of 55 minutes duration. [1]"; the chip opened the panel with the cited passage and the PDF at page 1 of 1 |
| 5 | Student: "When are groups 31 to 45 evaluated?" gave "No answer found" (gate, best similarity 0.294), with no hint of the faculty document |
| 6 | Faculty, same question: "Groups 31–45 are evaluated on Friday, 23 October 2026. [1]", Project Evaluation Schedule p. 1 |
| 7 | Student: "What is on the canteen menu on Fridays?" gave "No answer found" (gate, 0.173) |
| 8 | Student, follow-up in the step 4 conversation: "And how are its marks divided?" gave "The lab end term examination is of 15 marks: 5 Marks for the write up (Aim, Apparatus required, circuit diagram, model graph, observation table, result) and 10 marks for performance of the experiment and verification of output. [1]" |
| 9 | Faculty removed the student grant; the student's repeated step 4 question gave "No answer found", and both earlier citations showed "Source no longer available" with no title or link |

- The follow-up through the real model, both turns: turn 1 (step 4) one call, 796 tokens in and 34 out, $0.000097; turn 2 (step 8) the rewrite, 274 in and 21 out, and the answer, 838 in and 74 out, $0.000159 together. All live calls of Phase 6: 4, $0.00039 (steps 5, 7 and 9 never reached the model).

Reviews (ADR-028), listed in the pull request: the build session reviewed the branch against Sections 11.1, 12 and 16 and Appendix B and found no defect. The independent review (two fresh-context reviewers) found nothing in access control or in what reaches the model, and five defects in the screens, each fixed test first: a false empty list after deleting the last conversation of the last page (`ba88988`), the first answer's error banner staying after later answers and after a reload (`09dfb3a`), a draft typed during an answer being erased (`b757591`), a pending question carried into another conversation (`b40c754`), and focus lost when the citation panel closes (`d735ba2`).

Decisions taken for this phase: S19, S20 and the viewer (ADR-038).

Phase 5, grounded question answering, is done, merged through pull request [#7](https://github.com/uttkarsh-thakur26/capstone/pull/7) with every CI check green. D5 is `gpt-6-luna` through OpenAI (ADR-036); the other choices of the phase and S15 to S18 are in ADR-037. Next: Phase 6, conversations and the citation viewer.

Evidence (2026-11-01):

- `./mvnw verify`: 595 backend tests and the format check. The 19.2 "Question answering" tests: `QuestionAnsweringTest` (scripted fake chat model, prompt capture), `CitationValidatorTest`, `ModelAnswerTest`, `PromptAssemblerTest`; "Prompt injection": `PromptInjectionTest`, through real ingestion; the access matrix on asking, unscoped and scoped: `AnswerAccessMatrixTest` (60 cases). Mutation checks failed the tests as expected: no escaping, no evidence gate, no citation filter, a transaction around the model call, archived documents retrievable, every document readable.
- Frontend: 81 tests, lint, type check, build, format; `npm run gen:api` adds the two endpoints.
- Live run (dev profile, real `gpt-6-luna` and `qwen3-embedding:0.6b`): a FACULTY test account that can retrieve its four course PDFs and nothing else, so `Tuesday_lab_batches.pdf` never reached the model. One direct check call first: strict JSON schema, temperature 0.1 and reasoning effort `none` accepted, 0 reasoning tokens (116 in, 31 out). Then the five questions. 6 billed calls in all, $0.0013 together at $0.10 and $0.50 per million input and output tokens:

| Question | Outcome | Answer | Tokens in / out | Cost |
|---|---|---|---|---|
| How long is the lab exam and how is the time split? | ANSWERED, [1] Lab exam instructions p. 1 | "The lab exam is 55 minutes long. Students must complete the writing part in the first 15 minutes, then perform the experiment and show the output in the next 30 – 40 minutes. [1]" | 2,119 / 64 | $0.00024 |
| How are the 15 marks of the lab end-term exam divided? | ANSWERED, [1] Lab exam instructions p. 1 | "The 15 marks are divided into 5 marks for the write-up (Aim, Apparatus required, circuit diagram, model graph, observation table, result) and 10 marks for performing the experiment and verifying the output. [1]" | 2,298 / 69 | $0.00026 |
| When are groups 31 to 45 evaluated? | ANSWERED, [1] Project Evaluation Schedule p. 1 | "Groups 31–45 are evaluated on Friday, 23 October 2026. [1]" | 2,263 / 41 | $0.00025 |
| What is the deadline for project reports, and does it depend on the evaluation date? | ANSWERED, [1] Project Evaluation Schedule p. 1 | "Project reports and all project-related documents must be submitted by 12:00 noon on Friday, 9 October 2026. The deadline does not depend on the evaluation date; it applies to all groups, regardless of when they are evaluated. [1]" | 2,301 / 73 | $0.00027 |
| What is the re-exam fee if a student misses the lab exam? (unanswerable) | INSUFFICIENT_EVIDENCE: it passed the gate (similarity 0.610) and the model answered `answerable=false` | The fixed abstention message | 2,121 / 19 | $0.00022 |

  Retrieval took 124 to 144 ms, generation 1.4 to 3.2 s. The five questions scored 0.546 to 0.711 and three off-topic questions 0.162 to 0.282, so the provisional floor is 0.30 (ADR-037).

Reviews (ADR-028), listed in the pull request: the build session reviewed the branch against Sections 4.5, 11 and 15.3 and the exit criteria and found no defect. The independent review (two fresh-context reviewers) found nothing in what reaches the model and two low defects in persistence: two questions asked at once in one conversation could interleave their messages (fixed in `8fe3d91`, test first), and a failed answer that could not be stored was not logged (fixed in `5731980`, test first).

Decisions taken for this phase: D5 (ADR-036), S15 to S18 (ADR-037).

Phase 4, retrieval and search, is done: pull request [#6](https://github.com/uttkarsh-thakur26/capstone/pull/6) was merged into `main` on 2026-10-28 (merge commit `45cb199`) with every CI check green. Keyword search (RET-5) and the mode selector moved there from Phase 7 (ADR-035).

Evidence (2026-10-28):

- `./mvnw verify`: 479 backend tests and the format check. Access matrix on search, both modes, unscoped and scoped: `SearchAccessMatrixTest` (120 cases). The 19.2 "Retrieval" tests in both modes: `RetrievalTest`; through real ingestion: `SearchAfterIngestionTest`, which also checks that the query prefix reaches the model with queries only. Mutation checks failed the tests as expected: no archived filter, the predicate after `LIMIT`, a query without its prefix, a transaction around the model call.
- Frontend: 81 tests, lint, type check, build, format.
- Real model (dev profile, four course PDFs): the paraphrase "Am I allowed to keep my cell with me during the practical test?" puts the lab-exam instructions first in semantic mode (similarity 0.488) and finds nothing in keyword mode; the ISBN "9789332570948" is the only keyword result, while semantic mode ranks its passage second (0.323) behind another book list (0.336). Requests took 124 to 162 ms (semantic, with the query embedding) and 16 to 26 ms (keyword) on 9 chunks, too few for a p95.
- Browser (Chromium only): both modes, highlights, a result opening the PDF at page 2 of 2, no horizontal scroll at 375 px.

Reviews (ADR-028), listed in the pull request: the owner review found no defect; the independent review found that the viewed PDF stayed in memory after the viewer was left (fixed in `d5f8b9b`, test first).

Decisions taken for this phase: D11 and D13 (ADR-035), S14 (ADR-035).

Phase 3 is done: pull request [#4](https://github.com/uttkarsh-thakur26/capstone/pull/4) was merged into `main` on 2026-10-27 (merge commit `177bb34`). Evidence (2026-10-27):

- `./mvnw verify` on the branch passes with 328 backend tests and the format check. The "Ingestion" tests of 19.2 are in `IngestionPipelineTest`, `IndexCompatibilityCheckTest`, `EmbedderTest` and `IngestionWorkerTest`; the chunker and normalizer have unit tests. Removing `FOR UPDATE SKIP LOCKED` from the claim made the two-workers test fail.
- Frontend: 62 tests, lint, type check, build, format; `npm run gen:api` adds `TOO_MANY_CHUNKS`.
- Real model (`qwen3-embedding:0.6b` in Ollama, dev profile): five real course PDFs reached `INDEXED` in 4.3 s (1 to 9 pages, 46 chunks, 1024 dimensions). Comparing each chunk with PDFBox's text of every page: no chunk holds text from outside its stored page range, 38 of 46 ranges are confirmed by text found on one page only, and the other 8 are table chunks whose text repeats on several pages.
- Kill test (stuck timeout 2 min): a 400-page PDF was claimed at 10:46:33, the JVM was killed with `taskkill /F` during embedding at 10:46:41 (`PROCESSING`, 0 chunk rows), the backend restarted at 10:47:14, recovery returned it to `UPLOADED` with 1 attempt at 10:49:14, and it was `INDEXED` at 10:49:41 (454 chunks).
- Compose stack (`prod`): the backend container reached Ollama through `host.docker.internal`; in the browser a 300-page upload went from "Processing" to "Ready, 341 passages" on its own.

Reviews (ADR-028), listed in the pull request: the plan review found that Spring AI's `embed(...)` shortcuts drop `truncate` and `num_ctx` (fixed before the first commit); the owner review found that a finished run did not take the next queued document (fixed in `a9c1757`); the independent review found that a run claimed during a heartbeat was taken for a lost one (fixed in `bdfb5f7`, test first) and raised one point that was not a defect.

Decisions taken for this phase: D6 (ADR-033), D10, D12 and the Phase 3 corrections S11 to S13 (ADR-034).

Phase 2 is done: pull request [#3](https://github.com/uttkarsh-thakur26/capstone/pull/3) was merged into `main` on 2026-10-26 (merge commit `2f16711`) with every CI check green and 278 backend tests. Decisions: D8, D9, D15, D16 (ADR-029), S3, S7 to S10 (ADR-030), ADR-031, ADR-032.

Phase 1 is done: pull request [#2](https://github.com/uttkarsh-thakur26/capstone/pull/2) was merged into `main` on 2026-10-14 (merge commit `70b2041`) with every CI check green, 110 backend and 41 frontend tests passing. Report: [phase-reports/phase-1.md](phase-reports/phase-1.md). Decisions taken for that phase: D7 (ADR-020), D18 (ADR-021), password hashing (ADR-022), error model (ADR-023), audit log (ADR-024), specification corrections (ADR-025), one member builds the phase (ADR-026), frontend libraries (ADR-027).

Suggested before the demonstration: run Appendix B steps 1 and 2 by hand in Firefox (promote a registered user to FACULTY, upload with the real file picker and share). The screens were checked in Chromium only.

Phase 0 is done: pull request [#1](https://github.com/uttkarsh-thakur26/capstone/pull/1) was merged into `main` on 2026-10-07 (merge commit `4b51372`). Report: [phase-reports/phase-0.md](phase-reports/phase-0.md).

## Phases

| Phase | Name | Status | Date |
|---|---|---|---|
| 0 | Foundation | Done. All exit criteria met, CI green, reviewed by the owner (ADR-019), merged in `4b51372` | 2026-10-07 |
| 1 | Authentication and roles | Done. All exit criteria met, CI green, reviewed under ADR-028, merged in `70b2041` | 2026-10-14 |
| 2 | Document management | Done. All exit criteria met, CI green, reviewed under ADR-028, merged in `2f16711` | 2026-10-26 |
| 3 | Ingestion and indexing | Done. All exit criteria met, CI green, reviewed under ADR-028, merged in `177bb34` | 2026-10-27 |
| 4 | Retrieval and search (semantic and keyword) | Done. All exit criteria met, CI green, reviewed under ADR-028, merged through #6 | 2026-10-28 |
| 5 | Grounded question answering | Done. All exit criteria met, CI green, reviewed under ADR-028, merged through #7 | 2026-11-01 |
| 6 | Conversations and citation viewer | Done. All exit criteria met, CI green, reviewed under ADR-028, merged through #8 | 2026-11-02 |
| 7 | First evaluation run (keyword search moved to Phase 4, ADR-035) | Not started | |
| 8 | Hybrid search, summarization, comparison | Done. All exit criteria met, CI green, reviewed under ADR-028, merged through #10 | 2026-11-09 |
| 9 | Administration, audit, analytics (with the rate limits and Section 17 review of Phase 10) | Built on `feat/phase-9-admin`, waiting for the owner's click-through, review and merge | 2026-11-09 |
| 10 | Full evaluation and hardening | Not started | |
| 11 | Deployment, documentation and the final evaluation pack | Not started | |

## Course milestones

| Milestone | Date | State of the pack |
|---|---|---|
| Milestone-1 (proposal, 30 marks) | 18 October 2026, a Sunday: confirm whether this is the submission or the presentation date | Not tracked in the repository: `docs/course/` has not been started |
| Milestone-2 (MVP, 30 marks) | 23 November 2026 (30 November not confirmed, D20) | T1 is Phases 0 to 7 |
| Final panel and guide evaluation | End of semester, date not announced | |

## Open questions for the team

- Demonstration browser: Firefox on Windows, with Chrome as the fallback. Both keep the `Secure` refresh cookie on `http://localhost:3000` (checked 2026-10-07, ADR-013).
- D21: report template and reference style. D22: how stakeholder evidence is collected. Both are due for Milestone-1.
- Specification 27.8, to ask the guide or coordinator: the Milestone-2 date, the final evaluation date, report length, the final marks split, Milestone-1 and Milestone-2 submission details, prescribed templates, the university rule on plagiarism and responsible AI use (before the Milestone-1 submission), the Milestone 0 approval and Group ID, and whether demo time is inside the presentation slot.

## Known issues

- The upload-time report (2026-11-08) was not reproduced: the database, the API (UTC with `Z`) and the browser pane (IST) agreed. Times now show their zone, so a browser reporting another zone (Firefox's fingerprinting protection reports UTC) is visible. Tell the team if a wrong time is seen again, with the screen and the zone shown.
- No summary of a document above 40 pages: the owner's 93-page HR manual would need about 14 calls at the default limits. Raising `app.summary.max-pages` and `max-model-calls` makes it possible at a known cost; a multi-level reduce is not built.
- Hybrid ties at the top are common with two rankings; the keyword order breaks them (ADR-040). The Phase 10 evaluation compares the modes.
- A comparison of two different documents pairs their most alike sections, even when they are only loosely alike (similarity 0.2 to 0.3); the checks keep every reported difference true to both texts, but "removed" can mean "not in that passage of the other document".
- `docs/api.md` (error codes, ADR-023) and `docs/testing.md` (test cases, 19.4) are current only to the end of Phase 1. They are caught up when the owner asks for the documents.
- Backend tests need Docker running; without it they fail with "Could not find a valid Docker environment".
- On JDK 25, Mockito attaches itself as an agent during tests, which the JDK warns will be refused in a future release. It happens in every backend run, although no test creates a mock, because Spring Boot's test support loads Mockito. Fix: pass Mockito as `-javaagent` in the Surefire `argLine`.
- Ports 3000 and 5432 on `127.0.0.1` must be free for the Compose stack; a local PostgreSQL on 5432 clashes.
- google-java-format is held at 1.36.1 until a Spotless release supports a newer version (ADR-009).
- openapi-typescript runs through an npm override until a release supports TypeScript 6 (ADR-017).
- From Phase 1 the backend does not start without `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ALLOWED_ORIGINS` and, while no admin exists, `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`. A `.env` created before Phase 1 must add them (README quickstart), or `docker compose up` and development mode stop at startup.
- Expired and revoked refresh tokens stay in `refresh_token`; nothing deletes them yet. One row per sign-in and refresh, so the table grows slowly.
- The upload page still loads 835.6 kB of JavaScript on a first visit (its form, date and file-drop components); the other pages load less (ADR-039).
- The theme choice is kept in the browser, not with the account: another browser starts at System.
- Backend tests, flaky under load (2026-11-03): one `./mvnw verify` run with a Docker image build beside it failed `PromptInjectionTest` (top similarity 0.273, needs more than 0.30) and `RetrievalTest` (two scores of exactly 0.0). Both classes passed alone (27 of 27) and the next full run, with the machine idle, passed all 613. Likely an earlier test class's ingestion still writing chunks; not investigated yet.
- Text extraction loses table layout and reads two-column pages line by line across both columns (ING-5); table rows become long runs of text.
- After a crash (not a normal stop) a document being processed waits until its heartbeat is older than the stuck timeout, 5 minutes by default, before it is picked up again.
- A run that hangs inside PDFBox keeps its thread and its document until the application restarts; nothing interrupts it.
- The development backend (files in `data/files`) and the Compose backend (files on the `document-files` volume) share the database but not the files. A document uploaded in one mode and processed in the other fails with "The stored file could not be read."
- The parallel limit (ING-12) is per backend instance; T1 runs one.
- Spring AI 2.0.2 is built against Boot 4.1.2 and Framework 7.0.10; the project resolves 4.1.1 and 7.0.9 (VERSIONS.md).
- A crash between storing an uploaded file and inserting its row, or in the middle of writing it (a `.part` file), leaves a file on the volume that no row points at. Nothing removes it yet (ADR-031).
- One browser profile holds one refresh cookie, so two accounts cannot be signed in at the same time in one browser. A tab whose silent refresh returns another account ends its own session.
- Rate limits live in memory (SEC-8 allows it for one instance): a restart gives everyone a full budget again, including the day's AI cap.
- After an admin sets a new password, access tokens already issued keep working until they expire (15 minutes at most), as after changing one's own; refresh tokens end at once.
- `query_log` has no clean-up: one row per search, question, summary and comparison. Analytics start at its first row; nothing before Phase 9 was logged.
- `npm audit` in CI reports and does not fail the build until the final demonstration (ADR-041); the development-only packages have advisories it does not list (`--omit=dev`). The Maven dependency scan is Phase 10.
- In the live run the model put one `[n]` marker after a multi-sentence answer instead of after each sentence (11.3 rule 3). The citations were right; Phase 7 measures citation quality.
- In the demo, the student's question answered only by the faculty document scored 0.294 against the student's own document, just under the floor of 0.30. Had it passed, the model would have had to abstain. Phase 7 calibrates the floor.
- A follow-up costs two model calls (the rewrite and the answer); a first question costs one.
- The citation viewer is the browser's own (ADR-038): the snippet is shown beside the page, not highlighted in it.
- When Ollama hangs instead of refusing connections, a semantic search waits for the embedding retries and their 120 s read timeout before it answers 503.
- Keyword search needs every word of the query. A number that text extraction split at a line break ("978- 0134686097" in a PDF) is not found when typed whole.
- The viewer uses the browser's own PDF viewer. Checked in Chromium; check Firefox by hand. Browsers that show no PDF inside a page (most phones) need Download.
- Branch protection and rulesets are not available on this private repository with the owner's GitHub plan (the API answers 403, "Upgrade to GitHub Pro"). "A red build blocks merging" (19.3) is therefore kept by convention: do not merge a pull request whose checks are not green.

## Open specification issues

Found while checking the specification before Phase 0. Each must be settled, and the specification corrected through a decision record, by the phase shown.

| # | Issue | Sections | Resolve by | Status |
|---|---|---|---|---|
| S1 | The Milestone-1 checkpoint and 27.3 do not ask for the use-case diagram and level-0 DFD that Section 25 starts in the Milestone-1 pack. `deployment.md` is "first needed" at the final evaluation but used as a Milestone-2 source in 27.6 | 23, 25, 27.3, 27.6 | Milestone-1 pack | Open |
| S2 | `GET /auth/me` and `PUT /auth/me/password` are T1 endpoints that no phase builds; changing your own password has no audit action | 15.2, 14.2, 23 | Phase 1 | Resolved, ADR-025 |
| S3 | Data model: `audit_log` is append-only but inherits `updated_at`; tables written through JDBC cannot get JPA auditing; unique constraints for `document_grant` and `document_summary` and several foreign-key indexes are missing from 7.4; `document.owner_id` and `query_log.user_id` have no `ON DELETE` rule | 7.1, 7.2, 7.4 | Phase 1 (tables of that phase), then each phase for its own tables | Resolved: Phase 1 and 2 tables (ADR-024, ADR-025, ADR-030), `query_log` (ADR-041) |
| S4 | When `ACCESS_DENIED` is recorded is undefined (403 only, or also 404 on unreadable documents) | 14.2, 5.2 ACL-8 | Phase 1 | Resolved, ADR-024 |
| S5 | D18's default names no component set, and neither Tailwind nor a component set is in 3.1 | 26 D18, 3.1, 16.1 | Phase 1 | Resolved, ADR-021 |
| S6 | Problem Details: Boot's support is off by default and responses written by the Spring Security filter chain are not Problem Details | 15.1, 3.3 | Phase 1 | Resolved, ADR-023 |
| S7 | The status machine forbids PROCESSING to UPLOADED, which stuck-job recovery needs. Also unspecified: whether reprocess resets the attempt counter, which failure code applies when attempts run out, reprocessing a document that is UPLOADED or PROCESSING, and when UNSUPPORTED_TYPE and EMPTY occur during ingestion | 8.2, 8.3, 9.2 ING-2, 8.1 DOC-10 | Phase 2 | Resolved, ADR-030 |
| S8 | Access matrix: the Admin and owner cells of D5 (failed) and D6 (archived) say "read", but ACL-4 makes those documents unretrievable for everyone. The fixture does not say that D5 has leftover chunks | 5.4, 5.2 ACL-4, 19.2 | Phase 2 | Resolved, ADR-030 |
| S9 | 403 or 404 when a user can read a document but not manage it (for example Faculty A on D4), and whether a demoted owner keeps manage rights | 15.1, 5.2 ACL-2, ACL-8 | Phase 2 | Resolved, ADR-030 |
| S10 | `POST /documents/{id}/reprocess` is a T1 endpoint that no phase builds, but the Phase 3 tests need it | 15.2, 8.1 DOC-10, 23 | Phase 2 | Resolved, ADR-030 |
| S11 | ING-9 compares only dimensions, so a different model with the same dimension passes; the model name is configured twice; Flyway does not detect a changed dimension placeholder on an existing database | 9.2 ING-9, 18, 7.3 DATA-4 | Phase 3 | Resolved, ADR-034 |
| S12 | With the in-process ONNX model, the default chunk size of 400 tokens exceeds its 128 word-piece input, so D6 and D10 must be decided together | 3.4, 26 D6, D10, 9.2 ING-7 | Phase 3 | Resolved, ADR-033, ADR-034 |
| S13 | With virtual threads on, Boot's executor has no concurrency limit, so the ingestion bound (ING-12) needs a dedicated executor | 4.5, 9.2 ING-12 | Phase 3 | Resolved, ADR-034 |
| S14 | Read literally, the query-embedding model call sits inside the read-only retrieval transaction, against 4.5's rule | 4.5, 10 RET-2, RET-3 | Phase 4 | Resolved, ADR-035 |
| S15 | Creating a conversation and asking in it (`POST /conversations`, `POST /conversations/{id}/messages`) are needed by Phase 5 and by the Phase 7 harness but assigned to no phase | 15.2, 11.1, 23 | Phase 5 | Resolved, ADR-037 |
| S16 | Retries: RAG-6 allows one retry with a 60-second timeout, but Spring AI's defaults retry up to 10 times, the OpenAI module retries in its SDK, and `validateSchema()` can call the model four times | 11.4 RAG-6, 9.2 ING-8, 3.3 | Phase 5 | Resolved, ADR-037 |
| S17 | RAG-6 sets an explicit low temperature, but OpenAI's default chat model in Spring AI 2.0.1 rejects `temperature`; D5 must account for it | 11.4 RAG-6, 26 D5, 3.3 | Phase 5 | Resolved, ADR-037 |
| S18 | ADR-001 mentions a custom `DocumentRetriever`, but the only Spring AI component that uses one (`RetrievalAugmentationAdvisor`) cannot implement the evidence gate | 4.6, 11.1, 3.3 | Phase 5 | Resolved, ADR-037 |
| S19 | CONV-5 filters history only on readability, so answers citing archived (superseded) documents still reach the model | 12 CONV-5, 8.1 DOC-11 | Phase 6 | Resolved, ADR-038 |
| S20 | The Playwright end-to-end path needs a running backend with fake models, and no profile for that is defined; whether Playwright runs in CI is not stated | 16.4, 19.1, 19.3 | Phase 6 | Resolved, ADR-038 |
| S21 | The rules that a question's split never changes and that questions are written before being tried cannot be checked by any test or command | 20.2, 20.5 | Phase 7 | Open |
| S22 | The similarity floor is undefined in hybrid mode, where the fused score is not a similarity, and `message_citation.score` mixes three scales | 10, 11.1 step 5, 7.2 | Phase 8 | Resolved, ADR-040 |
| S23 | SUM-2 asks for "keywords" (T2), while keyword extraction is listed as T3 | 13.1 SUM-2, 2.3 | Phase 8 | Resolved, ADR-040 |
| S24 | Minor wording: SSO is both a non-goal and a T3 item; Appendix B's preamble contradicts steps 1 to 3; the reading lists of Phases 1, 2 and 6 omit 14.2 and Appendix B | 2.2, 2.3, Appendix B, 23 | Before the phase that reads them (Phase 1 for 14.2) | Resolved, ADR-025 |

## Phase reports

- [Phase 0](phase-reports/phase-0.md)
- [Phase 1](phase-reports/phase-1.md)
