# API

Started in Phase 1 (specification Section 25). The contract is in `docs/PROJECT_SPEC.md` Section 15. The full, current description is the OpenAPI document that springdoc generates from the code:

- In development mode, Swagger UI at <http://localhost:8080/swagger-ui/index.html> and the document at <http://localhost:8080/v3/api-docs>. Both are off in the `prod` profile (ADR-014).
- `./mvnw verify` saves the document to `backend/target/openapi.json`, and `npm run gen:api` turns it into `frontend/src/api/schema.d.ts`, the SPA's types (ADR-017). CI fails if the committed types differ.

## Conventions

- Base path `/api/v1`. JSON request and response bodies; responses declare their media type, so the generated types are exact.
- Authentication: `Authorization: Bearer <access token>` from `POST /auth/login` or `POST /auth/refresh`. The refresh token travels only in the `refresh_token` cookie (`docs/security.md`).
- Times are ISO 8601 in UTC; ids are UUID strings.
- Lists use the envelope `{ items, page, size, totalItems, totalPages }`, with `page` from 0 to 21,474,836 (a full page further on would start past the largest row offset a query can take), `size` from 1 to 100, and `sort` as `field` or `field,asc|desc` from an allow-list per endpoint.
- Errors are RFC 9457 Problem Details, `application/problem+json`, described in the OpenAPI document as `ProblemResponse` and attached to every operation as its default response (ADR-023):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The request is not valid.",
  "instance": "/api/v1/auth/register",
  "code": "VALIDATION_FAILED",
  "errors": [{ "field": "password", "message": "must be between 10 and 128 characters long" }]
}
```

## Error codes

The `code` is stable; clients decide on it, never on `detail`.

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A body or parameter breaks a validation rule; `errors` lists the fields |
| `BAD_REQUEST` | 400 | The body cannot be read, or a request the firewall rejects |
| `INVALID_SORT` | 400 | `sort` names a field that is not on the endpoint's allow-list |
| `EMAIL_DOMAIN_NOT_ALLOWED` | 400 | Registration with an email outside `APP_REGISTRATION_ALLOWED_EMAIL_DOMAINS` |
| `CURRENT_PASSWORD_INCORRECT` | 400 | Password change with a wrong current password |
| `UNAUTHENTICATED` | 401 | No access token, or one that is invalid, expired, or belongs to a disabled or missing account |
| `BAD_CREDENTIALS` | 401 | Sign-in failed, whatever the reason (AUTH-5) |
| `SESSION_EXPIRED` | 401 | Refresh without a usable refresh token |
| `FORBIDDEN` | 403 | Authenticated, but the role is not allowed |
| `ORIGIN_NOT_ALLOWED` | 403 | Refresh or logout from an origin not in `APP_SECURITY_ALLOWED_ORIGINS` |
| `NOT_FOUND` | 404 | No such resource, or no such path |
| `METHOD_NOT_ALLOWED` | 405 | The path exists, the method does not |
| `EMAIL_TAKEN` | 409 | Registration with an email that already has an account |
| `LAST_ADMIN` | 409 | The change would leave no enabled admin (AUTH-2) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | A body that is not JSON |
| `INTERNAL` | 500 | Anything unexpected; the detail never describes it |

## Endpoints so far

| Method and path | Access | Purpose |
|---|---|---|
| `GET /meta` | public | Product name and version |
| `POST /auth/register` | public | Create a STUDENT account; a `role` in the body is ignored |
| `POST /auth/login` | public | Access token and user; sets the refresh cookie |
| `POST /auth/refresh` | refresh cookie, Origin check | New access token; rotates the refresh token |
| `POST /auth/logout` | refresh cookie, Origin check | Ends the sign-in; always `204` |
| `GET /auth/me` | authenticated | The current user |
| `PUT /auth/me/password` | authenticated | Change one's own password; ends every session |
| `GET /admin/users` | ADMIN | List and search users: `query`, `role`, `enabled`, `page`, `size`, `sort` |
| `PATCH /admin/users/{id}` | ADMIN | Change `role`, `enabled` or both |
