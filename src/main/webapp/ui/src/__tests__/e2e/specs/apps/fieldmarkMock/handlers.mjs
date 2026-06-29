/**
 * Fieldmark mock handlers for the e2e mock server.
 *
 * Covers every HTTP call FieldmarkClientImpl makes when the browser
 * triggers a Fieldmark import:
 *
 *   1. Token exchange  POST /auth/exchange-long-lived-token
 *      → { token: "short-lived-mock-token" }
 *
 *   2. List notebooks  GET /notebooks/
 *      → array of FieldmarkNotebook objects
 *
 *   3. Get notebook    GET /notebooks/{notebookId}
 *      → single FieldmarkNotebook with ui-specification.fields
 *
 *   4. JSON records    GET /notebooks/{notebookId}/records
 *      → { records: [...] }
 *
 *   5. CSV export      GET /notebooks/{notebookId}/records/{formId}.csv
 *      → raw CSV bytes (formId = record type, e.g. "Primary")
 *
 *   6. ZIP files       GET /notebooks/{notebookId}/records/{formId}.zip
 *      → raw ZIP bytes
 *
 * Fixtures in ./fixtures/ are harvested from the real Fieldmark API.
 * They are the single source of truth: the mock returns them AND real-mode
 * assertions compare against the same values (drift detection).
 *
 * Handler format: { method, match(pathname) → boolean, respond(req) → { status, body, contentType? } }
 * body can be a plain object (JSON), string, or Buffer (binary).
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const fixturesDir = fileURLToPath(new URL("./fixtures/", import.meta.url));

const notebooksFixture = JSON.parse(
  readFileSync(`${fixturesDir}notebooks.json`, "utf8"),
);
const notebookFixture = JSON.parse(
  readFileSync(`${fixturesDir}notebookID.json`, "utf8"),
);
const recordsFixture = JSON.parse(
  readFileSync(`${fixturesDir}records.json`, "utf8"),
);
const csvFixture = readFileSync(`${fixturesDir}notebook.csv`);
const zipFixture = readFileSync(`${fixturesDir}FieldmarkFile.zip`);

export const fieldmarkHandlers = [
  // Token exchange — called before every authenticated request
  {
    method: "POST",
    match: (pathname) => pathname === "/auth/exchange-long-lived-token",
    respond: () => ({
      status: 200,
      body: { token: "short-lived-mock-token" },
    }),
  },

  // List all notebooks accessible to the user
  {
    method: "GET",
    match: (pathname) => pathname === "/notebooks/",
    respond: () => ({ status: 200, body: notebooksFixture }),
  },

  // Get a single notebook's metadata + ui-specification.fields
  // (matched before the records sub-paths)
  {
    method: "GET",
    match: (pathname) =>
      /^\/notebooks\/[^/]+$/.test(pathname),
    respond: () => ({ status: 200, body: notebookFixture }),
  },

  // JSON records export
  {
    method: "GET",
    match: (pathname) =>
      /^\/notebooks\/[^/]+\/records$/.test(pathname),
    respond: () => ({ status: 200, body: recordsFixture }),
  },

  // CSV records export (formId = record type, e.g. "Primary")
  {
    method: "GET",
    match: (pathname) =>
      /^\/notebooks\/[^/]+\/records\/[^/]+\.csv$/.test(pathname),
    respond: () => ({
      status: 200,
      body: csvFixture,
      contentType: "text/csv",
    }),
  },

  // ZIP files export
  {
    method: "GET",
    match: (pathname) =>
      /^\/notebooks\/[^/]+\/records\/[^/]+\.zip$/.test(pathname),
    respond: () => ({
      status: 200,
      body: zipFixture,
      contentType: "application/zip",
    }),
  },
];
