# Prompt: Add a Web UI for Document Management to stc-mcp-server

## Context

`stc-mcp-server` is a Node.js application deployed on Cloud Foundry. It serves as an MCP server with a `search_stc_documents` tool that performs similarity search against a pgvector database. The app already has an HTTP endpoint for document upload/ingestion. It is bound to a Cloud Foundry pgvector service instance and an embedding model service instance via VCAP_SERVICES.

The vector table has these columns:
- `content` TEXT — the chunk text
- `embedding` VECTOR(1536) — the embedding vector
- `doc_title` TEXT — the original filename

## Goal

Add a web UI served from `stc-mcp-server` that provides three capabilities: uploading documents for ingestion, clearing the vector database, and viewing stats about what's stored. The UI should be protected by a simple password gate.

## UI Framework Recommendation

Use **plain HTML + vanilla JavaScript** served as static files via Express (`express.static`). For styling, use **Pico CSS** (a classless CSS framework you can load from CDN: `https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css`). This avoids any build step, keeps the footprint tiny, and looks clean out of the box. No React, no bundler, no npm UI dependencies.

Put all static UI files in a `public/` directory and mount it in Express:

```js
app.use(express.static('public'));
```

## Authentication

Implement simple password protection using an environment variable and Express middleware. This isn't full user-auth — it's a lightweight gate to prevent casual unauthorized access on the CF foundation.

**Environment variable:** `ADMIN_UI_PASSWORD`

Set this in the CF environment:
```bash
cf set-env stc-mcp-server ADMIN_UI_PASSWORD <some-password>
```

### Backend approach

Add a session-based auth flow using `express-session` (add it as a dependency):

1. **`POST /api/login`** — accepts JSON `{ "password": "..." }`. Compare against `process.env.ADMIN_UI_PASSWORD`. If matched, set `req.session.authenticated = true` and return 200. If not, return 401.

2. **`POST /api/logout`** — destroys the session, returns 200.

3. **Auth middleware** — apply to all `/api/*` routes *except* `/api/login`. Check `req.session.authenticated`; if not set, return 401 JSON. **Do not apply this middleware to the MCP transport routes or the existing ingestion endpoint** — only to the new UI-backing API routes. If the existing upload endpoint is being reused by the UI, gate only the UI-specific routes (`/api/stats`, `DELETE /api/documents`) and the UI's upload call behind auth.

4. **Session config** — use a secret from an environment variable (`SESSION_SECRET`) or fall back to a random string generated at startup. Set `cookie.secure` based on whether the app is behind HTTPS (it will be on CF). Use the default in-memory session store — this is a low-traffic internal tool and sessions don't need to survive restarts.

### Frontend approach

On page load, make a lightweight call (e.g., `GET /api/stats`) and check for a 401 response. If 401, show a login form instead of the main UI. The login form has a single password field and a submit button — no username needed. On successful login, hide the login form and show the main UI. Include a "Log out" link in the page header that calls `POST /api/logout` and returns to the login form.

## Required API Endpoints (backend)

Add these Express routes. All return JSON and are protected by the auth middleware described above.

### 1. `POST /api/upload`

The upload endpoint may already exist. Ensure it accepts `multipart/form-data` with a file field, chunks the document text, generates embeddings via the bound embedding model, and inserts rows into the vector table with `content`, `embedding`, and `doc_title` (set to the original filename). Return a JSON response indicating success and the number of chunks created. If the existing upload route is at a different path, either reuse it from the frontend or add a new auth-gated route that delegates to the same ingestion logic.

### 2. `DELETE /api/documents`

Truncate or delete all rows from the vector table. Return JSON confirming the action. This is the "clear database" operation.

### 3. `GET /api/stats`

Query the vector table and return JSON with:
- `totalChunks`: `SELECT COUNT(*) FROM <table>`
- `uniqueDocuments`: `SELECT COUNT(DISTINCT doc_title) FROM <table>`
- `documents`: `SELECT doc_title, COUNT(*) as chunk_count FROM <table> GROUP BY doc_title ORDER BY doc_title`

## Frontend: `public/index.html`

Single-page layout using Pico CSS conventions (`<main class="container">`).

### Login State

- A centered card with a password input and "Log in" button
- On failed login, show an inline error message

### Authenticated State (three sections)

**Header** — App title ("stc-mcp-server Admin") and a "Log out" link/button aligned right.

**Section 1 — Database Stats (top of page)**
- On page load (after auth), fetch `GET /api/stats` and display:
  - Total chunks and unique document count as a summary line
  - A `<table>` listing each `doc_title` and its chunk count
- Include a refresh button that re-fetches stats
- If the table is empty, show a friendly "No documents ingested yet" message

**Section 2 — Upload Document**
- A file input (`<input type="file">`) and an "Upload" button
- On submit, POST the file to `/api/upload` as `multipart/form-data` using `fetch` and a `FormData` object
- Show a `<progress>` element or spinner during upload (embedding/chunking can be slow)
- On success, display the number of chunks created and auto-refresh the stats section
- Accept common document types: `.pdf`, `.txt`, `.md`, `.docx` — or whatever the existing ingestion endpoint supports

**Section 3 — Clear Database**
- A button labeled "Clear All Documents" styled as a destructive action (Pico CSS supports `class="contrast"` or `class="outline secondary"`)
- On click, show a confirmation dialog (`confirm()` is fine) before calling `DELETE /api/documents`
- On success, auto-refresh the stats section

## General Implementation Notes

- **Error handling**: Every fetch call should catch errors and display them inline in a `<div role="alert">` near the relevant section. If any API call returns 401, redirect back to the login view (session may have expired).
- **CORS**: Not needed — the UI is served from the same origin as the API.
- **Keep the existing MCP transport and endpoints untouched.** The UI is additive. Don't refactor the MCP stdio/SSE transport or the `search_stc_documents` tool. Auth middleware must not interfere with MCP protocol routes.
- **CF manifest**: No changes needed to `manifest.yml` for serving static files. You will need to add the `ADMIN_UI_PASSWORD` environment variable (via `cf set-env` or in the manifest under `env:`). If adding to the manifest, use a placeholder value and document that it must be changed before push.
- **Database connection**: Reuse the existing pg client/pool that `stc-mcp-server` already uses for vector queries. Don't create a second connection pool.
- **File field name**: Check what field name the existing upload endpoint expects (commonly `file` or `document`) and match it in the frontend `FormData.append()` call.
- **New dependency**: `express-session` — add via `npm install express-session`.

## Suggested File Structure (additions only)

```
public/
  index.html      ← single-page UI (HTML + inline <script> + Pico CSS CDN)
```

If you prefer separating JS, you can use `public/app.js`, but inline is fine for this scope.
