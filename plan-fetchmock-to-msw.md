# Plan: replace vitest-fetch-mock with MSW in jsdom unit tests

## Scope and delivery strategy

This is intentionally a **single-pass migration**. Add the jsdom MSW server,
migrate every fetch-mock test, remove `vitest-fetch-mock`, and land the result
together. Do not merge or release the infrastructure change separately while
tests still depend on the global fetch mock.

## Current state

- `vitest-fetch-mock` is enabled globally in `src/__tests__/setup.ts`
  (`createFetchMock(vi)` + `enableMocks()`), typed via `custom.d.ts`
  (`import "vitest-fetch-mock"`).
- MSW 2.14.6 is already a dependency and is used by Browser Mode specs.
  `src/__tests__/browserSetup.ts` uses `setupWorker` from `msw/browser`, with
  shared handler modules such as `mswAppShellHandlers.ts` and
  `src/__tests__/mocks/*`.
- 17 jsdom test files use `fetchMock` (215 references in total):
  - `mockResponseOnce` ×103
  - `mockResponse` ×22
  - `mockRejectOnce` ×12
  - `resetMocks` ×15
  - request/call observations: 51 `expect(fetchMock)...` assertions and 9
    direct `fetchMock.mock.calls` accesses
- Those observations cover more than JSON bodies: URLs, query parameters,
  methods, headers, call counts, plain-text bodies, JSON bodies, and `FormData`.
- Heaviest files:
  - `modules/raid/__tests__/{mutations,queries}.test.ts` (41/39 references)
  - `Inventory/.../ElnRecordInfoDialog.test.tsx` (26)
  - `modules/stoichiometry/__tests__/mutations.test.ts` (24)
  - `modules/groups/__tests__/queries.test.ts` (22)

## Key semantic differences

### URL and method matching

fetch-mock responses are generally consumed in call order and need not identify
the request. MSW matches by URL and HTTP method. Every migrated test must name
the endpoint and method it mocks.

Use the path, without a query string, in the handler. Inspect query parameters
through `new URL(request.url).searchParams`; for example, handle
`/integration/integrationInfo?name=RAID` with a handler for
`/integration/integrationInfo` and separately verify `name=RAID`.

### Handler lifetime

Per-test handlers are removed by the global `server.resetHandlers()` hook, so
normal test responses should use reusable handlers:

```ts
server.use(http.get("/api/v1/example", () => HttpResponse.json(response)));
```

Do **not** mechanically translate every `mockResponseOnce` to `{ once: true }`.
Use `{ once: true }` only when the test deliberately exercises multiple
responses from the same endpoint. Prefer a response queue in one handler when
order is part of the behavior:

```ts
const responses = [firstResponse, secondResponse];
let requestCount = 0;
server.use(
  http.get("/api/v1/example", () => {
    const response = responses[Math.min(requestCount, responses.length - 1)];
    requestCount += 1;
    return HttpResponse.json(response);
  }),
);

await operation();
expect(requestCount).toBe(2);
```

### Network errors

`fetchMock.mockRejectOnce(new Error("Network down"))` can inject an arbitrary
error message. `HttpResponse.error()` models a real fetch network failure and
does not preserve that custom message. Migrate these tests to
`HttpResponse.error()` and assert rejection/network-failure behavior without
depending on an engine-specific message, unless the application itself wraps
the failure in a stable domain error.

HTTP errors are not network errors. Model them with a response carrying the
intended body, status, and status text.

## Steps

### 1. Add the jsdom MSW server

- Add `src/__tests__/mswServer.ts`:

  ```ts
  import { setupServer } from "msw/node";

  export const server = setupServer();
  ```

- In `src/__tests__/setup.ts`:
  - remove `createFetchMock`, `vi` if no longer otherwise used, and
    `fetchMocker.enableMocks()`;
  - import `server` and `beforeAll`;
  - add:

    ```ts
    beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
    afterAll(() => server.close());
    ```

  - add `server.resetHandlers()` to the existing `afterEach` cleanup hook,
    after React cleanup and storage cleanup, so handlers remain active during
    component unmounting.
- Remove `import "vitest-fetch-mock"` from `custom.d.ts`.

`setupServer` operates at the network layer, not only on `fetch`. Strict
unhandled-request handling can therefore expose unintended requests from other
clients (including XHR/axios) elsewhere in the jsdom suite. Treat those as
missing handlers or test leaks, and use the full jsdom suite as the check for
global impact.

### 2. Migrate all 17 test files

Import `http` and `HttpResponse` from `msw`, and `server` from the shared jsdom
test server. Register handlers inside the test or its `beforeEach`, close to the
behavior they arrange.

#### Response translations

JSON success:

```ts
server.use(http.get(url, () => HttpResponse.json(value)));
```

JSON HTTP error, preserving all response semantics used by the application:

```ts
server.use(
  http.get(url, () =>
    HttpResponse.json(errorBody, {
      status: 500,
      statusText: "Internal Server Error",
    }),
  ),
);
```

Empty response, including `201` success:

```ts
server.use(http.post(url, () => new HttpResponse(null, { status: 201 })));
```

Plain text:

```ts
server.use(http.get(url, () => new HttpResponse("text response")));
```

Malformed JSON (used by schema/parsing error tests):

```ts
server.use(
  http.get(
    url,
    () =>
      new HttpResponse("Not valid JSON", {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
  ),
);
```

Network failure:

```ts
server.use(http.get(url, () => HttpResponse.error()));
```

Preserve `status`, `statusText`, headers, raw body format, and empty-body
behavior from each existing fetch-mock response. Do not use
`HttpResponse.json()` for malformed JSON, plain text, or empty bodies.

#### Request observations

Capture request details in the handler and assert after the operation under
test completes. Do not put assertions in the handler resolver: an exception
there can be represented as a mocked server failure and obscure the assertion.

URL, query, method, and headers:

```ts
let capturedRequest:
  | { method: string; url: URL; authorization: string | null }
  | undefined;

server.use(
  http.get(url, ({ request }) => {
    capturedRequest = {
      method: request.method,
      url: new URL(request.url),
      authorization: request.headers.get("Authorization"),
    };
    return HttpResponse.json(response);
  }),
);

await operation();

expect(capturedRequest).toMatchObject({
  method: "GET",
  authorization: `Bearer ${token}`,
});
expect(capturedRequest?.url.searchParams.get("pageNumber")).toBe("1");
```

JSON request body:

```ts
let capturedBody: unknown;
server.use(
  http.post(url, async ({ request }) => {
    capturedBody = await request.json();
    return new HttpResponse(null, { status: 201 });
  }),
);
```

Form data:

```ts
let capturedFormData: FormData | undefined;
server.use(
  http.post(url, async ({ request }) => {
    capturedFormData = await request.formData();
    return HttpResponse.json(response);
  }),
);
```

Call counts:

```ts
const requests: URL[] = [];
server.use(
  http.get(url, ({ request }) => {
    requests.push(new URL(request.url));
    return HttpResponse.json(response);
  }),
);

await operation();
expect(requests).toHaveLength(1);
```

Delete each file's `fetchMock.resetMocks()`; the global `resetHandlers()` hook
provides test isolation. Retain `vi.clearAllMocks()` where the file has other
spies or mocks that require it.

#### Shared handlers and fixtures

Reuse environment-neutral fixtures and handler factories only where they
already match the endpoint and behavior:

- tags has a reusable handler factory in
  `components/Tags/__tests__/mocks/tagsComboboxMocks.ts`;
- stoichiometry has reusable fixtures and a molecule-info handler in
  `tinyMCE/stoichiometry/__tests__/mocks/stoichiometryMocks.ts`;
- the main `/api/v1/stoichiometry` handlers currently live inline in Browser
  Mode specs and may be extracted if doing so genuinely removes duplication;
- there are currently no reusable RAID MSW handlers, so add local handlers or
  extract small handler factories shared by the RAID suites;
- `oauthTokenMocks.ts` is relevant only to tests that actually request the
  OAuth token endpoint.

Keep shared handler modules independent of `setupWorker` and `setupServer` so
they can be imported by both browser and jsdom tests.

### 3. Cleanup

- Remove `vitest-fetch-mock` from root `package.json`.
- Update `pnpm-lock.yaml` with `pnpm install` from the repository root.
- Confirm all production/test references are gone:

  ```bash
  rg 'fetchMock|vitest-fetch-mock' src/main/webapp/ui package.json pnpm-lock.yaml
  ```

  The command must return no source/package references after lockfile cleanup.

### 4. Verify

Run from the repository root:

```bash
pnpm run test
pnpm run tsc
pnpm run lint
```

`pnpm run test` is mandatory because the server and strict unhandled-request
policy are global to the jsdom suite.

Browser Mode has a separate setup file, so the infrastructure change alone
does not require the full browser suite. If shared handlers or fixtures used by
Browser Mode are changed or extracted, run the affected browser specs and then
run `pnpm run test-browser` once as the regression check.

## Order of file migration

Within the single working pass, migrate from simpler files to the files that
exercise the most response and request variants:

1. TinyMCE gallery/stoichiometry tests, AddTag, and RaidIntegrationCard.
2. Workspace and share modules (includes text, empty-body, and `FormData`
   request handling).
3. Groups and stoichiometry query/mutation tests (headers, query parameters,
   schema failures, status text, and network failures).
4. ElnRecordInfoDialog.
5. RAID queries/mutations (largest suites; many status/error variants and
   request assertions).

Do not commit or hand off a partially migrated state. The final change must
contain infrastructure, all 17 migrations, dependency cleanup, and verification
results together.

## Estimate

- Infrastructure and common request-capture patterns: 1–2 hours.
- Test migration and error-semantics review: 1–2 days.
- Full-suite verification and cleanup: allow additional time for unrelated
  unhandled requests exposed by the global network interceptor.

Single PR, single-pass delivery.

## Risks

- Strict URL/method matching may reveal tests that were receiving a response
  intended for a different request. Treat these as test corrections.
- The global node server can reveal unhandled XHR/axios traffic outside the 17
  fetch-mock files. Diagnose each request rather than weakening
  `onUnhandledRequest: "error"` globally.
- Network-error assertions that currently depend on an arbitrary injected
  message must be changed to stable behavior assertions.
- Omitting `statusText`, raw content type, or empty-body semantics can alter the
  application error path even when the status code is correct.
- Relative request URLs are supported by the pinned MSW version in jsdom.
  Continue using path-based handlers such as `http.get("/api/v1/foo", ...)`;
  do not introduce test-only absolute URLs.
