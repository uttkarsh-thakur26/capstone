# Progress

Updated 2026-10-14. Format: specification Section 24.8.

## Current phase

**Phase 1, authentication and roles: every exit criterion is met; the phase is not closed yet.** Built on branch `feat/auth-roles` entirely by Uttkarsh, because the other members are not available yet (ADR-026, a recorded exception to 24.1). Report: [phase-reports/phase-1.md](phase-reports/phase-1.md).

Evidence (2026-10-07, last code commit `1ce5d59`): `./mvnw verify` passes with 107 tests, the frontend checks with 39 tests, a clean clone passes all of them and starts with the README quickstart, and the browser checks of exit criteria 3 and 4 passed in Chromium 152 (`docs/testing.md`, TC-1-01 to TC-1-25 and TC-1-M01 to TC-1-M07). The refresh cookie was checked by hand in Firefox 153, the demonstration browser (TC-1-M02).

To close Phase 1:

1. Open: open the pull request; the branch is pushed (2026-10-14).
2. Open: CI green on the pull request, including the new sign-in step of the stack job.
3. Open: review under ADR-028, the standing exception to 24.2 while no teammate can review: the owner's review and an independent review in a fresh session, with the findings and fixes listed in the pull request.
4. Open: merge, then mark Phase 1 done here.

Suggested before the demonstration: run Appendix B step 1 by hand in Firefox (sign in as the bootstrap admin, promote a registered user to FACULTY). The screens were checked in Chromium only.

Decisions taken for this phase: D7 (ADR-020), D18 (ADR-021), password hashing (ADR-022), error model (ADR-023), audit log (ADR-024), specification corrections (ADR-025), one member builds the phase (ADR-026), frontend libraries (ADR-027).

Phase 0 is done: pull request [#1](https://github.com/uttkarsh-thakur26/capstone/pull/1) was merged into `main` on 2026-10-07 (merge commit `4b51372`). Report: [phase-reports/phase-0.md](phase-reports/phase-0.md).

## Phases

| Phase | Name | Status | Date |
|---|---|---|---|
| 0 | Foundation | Done. All exit criteria met, CI green, reviewed by the owner (ADR-019), merged in `4b51372` | 2026-10-07 |
| 1 | Authentication and roles | All exit criteria met with evidence; awaiting push, CI, review and merge | 2026-10-07 |
| 2 | Document management | Not started | |
| 3 | Ingestion and indexing | Not started | |
| 4 | Retrieval and semantic search | Not started | |
| 5 | Grounded question answering | Not started | |
| 6 | Conversations and citation viewer | Not started | |
| 7 | Keyword baseline and first evaluation run | Not started | |
| 8 | Hybrid search, summarization, comparison | Not started | |
| 9 | Administration, audit, analytics | Not started | |
| 10 | Full evaluation and hardening | Not started | |
| 11 | Deployment, documentation and the final evaluation pack | Not started | |

## Course milestones

| Milestone | Date | State of the pack |
|---|---|---|
| Milestone-1 (proposal, 30 marks) | 18 October 2026, a Sunday: confirm whether this is the submission or the presentation date | Not tracked in the repository: `docs/course/` has not been started |
| Milestone-2 (MVP, 30 marks) | 23 November 2026 (30 November not confirmed, D20) | T1 is Phases 0 to 7 |
| Final panel and guide evaluation | End of semester, date not announced | |

## Open questions for the team

- D1: the product name. Phase 0 uses the working description through `APP_DISPLAY_NAME`.
- D5 and D6: provisional chat and embedding model choices, needed for the Milestone-1 proposal.
- Demonstration browser: Firefox on Windows, with Chrome as the fallback. Both keep the `Secure` refresh cookie on `http://localhost:3000` (checked 2026-10-07, ADR-013).
- D21: report template and reference style. D22: how stakeholder evidence is collected. Both are due for Milestone-1.
- Specification 27.8, to ask the guide or coordinator: the Milestone-2 date, the final evaluation date, report length, the final marks split, Milestone-1 and Milestone-2 submission details, prescribed templates, the university rule on plagiarism and responsible AI use (before the Milestone-1 submission), the Milestone 0 approval and Group ID, and whether demo time is inside the presentation slot.

## Known issues

- Backend tests need Docker running; without it they fail with "Could not find a valid Docker environment".
- On JDK 25, Mockito attaches itself as an agent during tests, which the JDK warns will be refused in a future release. It happens in every backend run, although no test creates a mock, because Spring Boot's test support loads Mockito. Fix: pass Mockito as `-javaagent` in the Surefire `argLine`.
- Ports 3000 and 5432 on `127.0.0.1` must be free for the Compose stack; a local PostgreSQL on 5432 clashes.
- google-java-format is held at 1.36.1 until a Spotless release supports a newer version (ADR-009).
- openapi-typescript runs through an npm override until a release supports TypeScript 6 (ADR-017).
- From Phase 1 the backend does not start without `APP_SECURITY_JWT_SECRET`, `APP_SECURITY_ALLOWED_ORIGINS` and, while no admin exists, `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`. A `.env` created before Phase 1 must add them (README quickstart), or `docker compose up` and development mode stop at startup.
- Expired and revoked refresh tokens stay in `refresh_token`; nothing deletes them yet. One row per sign-in and refresh, so the table grows slowly.
- Throttling of repeated failed sign-ins (AUTH-5) is `[T2]` and not built.
- The JavaScript bundle is 523 kB (162 kB gzipped), above Vite's 500 kB advisory limit. Splitting routes into separate chunks is left for later.
- One browser profile holds one refresh cookie, so two accounts cannot be signed in at the same time in one browser.
- Branch protection and rulesets are not available on this private repository with the owner's GitHub plan (the API answers 403, "Upgrade to GitHub Pro"). "A red build blocks merging" (19.3) is therefore kept by convention: do not merge a pull request whose checks are not green.

## Open specification issues

Found while checking the specification before Phase 0. Each must be settled, and the specification corrected through a decision record, by the phase shown.

| # | Issue | Sections | Resolve by | Status |
|---|---|---|---|---|
| S1 | The Milestone-1 checkpoint and 27.3 do not ask for the use-case diagram and level-0 DFD that Section 25 starts in the Milestone-1 pack. `deployment.md` is "first needed" at the final evaluation but used as a Milestone-2 source in 27.6 | 23, 25, 27.3, 27.6 | Milestone-1 pack | Open |
| S2 | `GET /auth/me` and `PUT /auth/me/password` are T1 endpoints that no phase builds; changing your own password has no audit action | 15.2, 14.2, 23 | Phase 1 | Resolved, ADR-025 |
| S3 | Data model: `audit_log` is append-only but inherits `updated_at`; tables written through JDBC cannot get JPA auditing; unique constraints for `document_grant` and `document_summary` and several foreign-key indexes are missing from 7.4; `document.owner_id` and `query_log.user_id` have no `ON DELETE` rule | 7.1, 7.2, 7.4 | Phase 1 (tables of that phase), then each phase for its own tables | Phase 1 tables resolved (ADR-024, ADR-025); open for Phases 2 and 9 |
| S4 | When `ACCESS_DENIED` is recorded is undefined (403 only, or also 404 on unreadable documents) | 14.2, 5.2 ACL-8 | Phase 1 | Resolved, ADR-024 |
| S5 | D18's default names no component set, and neither Tailwind nor a component set is in 3.1 | 26 D18, 3.1, 16.1 | Phase 1 | Resolved, ADR-021 |
| S6 | Problem Details: Boot's support is off by default and responses written by the Spring Security filter chain are not Problem Details | 15.1, 3.3 | Phase 1 | Resolved, ADR-023 |
| S7 | The status machine forbids PROCESSING to UPLOADED, which stuck-job recovery needs. Also unspecified: whether reprocess resets the attempt counter, which failure code applies when attempts run out, reprocessing a document that is UPLOADED or PROCESSING, and when UNSUPPORTED_TYPE and EMPTY occur during ingestion | 8.2, 8.3, 9.2 ING-2, 8.1 DOC-10 | Phase 2 | Open |
| S8 | Access matrix: the Admin and owner cells of D5 (failed) and D6 (archived) say "read", but ACL-4 makes those documents unretrievable for everyone. The fixture does not say that D5 has leftover chunks | 5.4, 5.2 ACL-4, 19.2 | Phase 2 | Open |
| S9 | 403 or 404 when a user can read a document but not manage it (for example Faculty A on D4), and whether a demoted owner keeps manage rights | 15.1, 5.2 ACL-2, ACL-8 | Phase 2 | Open |
| S10 | `POST /documents/{id}/reprocess` is a T1 endpoint that no phase builds, but the Phase 3 tests need it | 15.2, 8.1 DOC-10, 23 | Phase 2 | Open |
| S11 | ING-9 compares only dimensions, so a different model with the same dimension passes; the model name is configured twice; Flyway does not detect a changed dimension placeholder on an existing database | 9.2 ING-9, 18, 7.3 DATA-4 | Phase 3 | Open |
| S12 | With the in-process ONNX model, the default chunk size of 400 tokens exceeds its 128 word-piece input, so D6 and D10 must be decided together | 3.4, 26 D6, D10, 9.2 ING-7 | Phase 3 | Open |
| S13 | With virtual threads on, Boot's executor has no concurrency limit, so the ingestion bound (ING-12) needs a dedicated executor | 4.5, 9.2 ING-12 | Phase 3 | Open |
| S14 | Read literally, the query-embedding model call sits inside the read-only retrieval transaction, against 4.5's rule | 4.5, 10 RET-2, RET-3 | Phase 4 | Open |
| S15 | Creating a conversation and asking in it (`POST /conversations`, `POST /conversations/{id}/messages`) are needed by Phase 5 and by the Phase 7 harness but assigned to no phase | 15.2, 11.1, 23 | Phase 5 | Open |
| S16 | Retries: RAG-6 allows one retry with a 60-second timeout, but Spring AI's defaults retry up to 10 times, the OpenAI module retries in its SDK, and `validateSchema()` can call the model four times | 11.4 RAG-6, 9.2 ING-8, 3.3 | Phase 5 | Open |
| S17 | RAG-6 sets an explicit low temperature, but OpenAI's default chat model in Spring AI 2.0.1 rejects `temperature`; D5 must account for it | 11.4 RAG-6, 26 D5, 3.3 | Phase 5 | Open |
| S18 | ADR-001 mentions a custom `DocumentRetriever`, but the only Spring AI component that uses one (`RetrievalAugmentationAdvisor`) cannot implement the evidence gate | 4.6, 11.1, 3.3 | Phase 5 | Open |
| S19 | CONV-5 filters history only on readability, so answers citing archived (superseded) documents still reach the model | 12 CONV-5, 8.1 DOC-11 | Phase 6 | Open |
| S20 | The Playwright end-to-end path needs a running backend with fake models, and no profile for that is defined; whether Playwright runs in CI is not stated | 16.4, 19.1, 19.3 | Phase 6 | Open |
| S21 | The rules that a question's split never changes and that questions are written before being tried cannot be checked by any test or command | 20.2, 20.5 | Phase 7 | Open |
| S22 | The similarity floor is undefined in hybrid mode, where the fused score is not a similarity, and `message_citation.score` mixes three scales | 10, 11.1 step 5, 7.2 | Phase 8 | Open |
| S23 | SUM-2 asks for "keywords" (T2), while keyword extraction is listed as T3 | 13.1 SUM-2, 2.3 | Phase 8 | Open |
| S24 | Minor wording: SSO is both a non-goal and a T3 item; Appendix B's preamble contradicts steps 1 to 3; the reading lists of Phases 1, 2 and 6 omit 14.2 and Appendix B | 2.2, 2.3, Appendix B, 23 | Before the phase that reads them (Phase 1 for 14.2) | Resolved, ADR-025 |

## Phase reports

- [Phase 0](phase-reports/phase-0.md)
- [Phase 1](phase-reports/phase-1.md)
