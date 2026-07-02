#!/usr/bin/env node
/**
 * E2E mock server — MSW handlers via @mswjs/http-middleware, hosted by Hono.
 *
 * WHY a real HTTP server and not MSW's Node interceptor
 * RSpace integration calls are made server-side by the Java backend
 * (Spring RestTemplate). The browser only calls /api/v1/<integration>/...
 * on the RSpace server — it never reaches PubChem, Fieldmark, etc. directly.
 *
 * MSW's msw/node patches Node's http module to intercept outgoing requests
 * from the same Node process — it cannot intercept a Java JVM's network calls.
 *
 * Instead, we start a real TCP listener. JVM startup args override every
 * integration's base URL (pubchem.base.url, fieldmark.api.url, …) to point
 * at http://localhost:9099, so Spring RestTemplate calls land here.
 *
 * WHY MSW handlers (not a custom format)
 * MSW's http.* API is the same format used by Vitest Browser Mode component
 * tests (via worker.use()), so fixture data and handler patterns can be
 * shared across both test layers without translation.
 *
 * HOW it works
 * MSW handlers run as Express-style middleware (@mswjs/http-middleware) with
 * Hono (@hono/node-server) as the fallback listener. Node's IncomingMessage
 * is patched with the Express-compatible properties MSW middleware expects
 * (protocol, get, header). Unhandled requests fall through to Hono.
 *
 * Each integration exports a `handlers` array of MSW RequestHandlers:
 *
 *   export const myHandlers = [
 *     http.get(/\/some\/path/, () => HttpResponse.json(fixture)),
 *     http.post("/other/path", () => HttpResponse.json({ ok: true })),
 *   ];
 *
 * ADDING AN INTEGRATION
 * 1. Create  src/__tests__/mocks/<name>Mock/handlers.mjs
 * 2. Add fixture JSON files under  …/<name>Mock/fixtures/
 * 3. Import and spread its handlers into the `handlers` array below.
 * 4. Override the integration's base URL in the CI jetty:run-war command
 *    (and in the local runbook in docs/e2e-mocking.md).
 *
 * USAGE
 *  node src/__tests__/e2e/mockServer.mjs        # default port 9099
 *  E2E_MOCK_PORT=9100 node ...                  # override port
 *
 * Playwright's webServer block starts this automatically in mock mode.
 * See playwright-e2e.config.ts → webServer.
 */

import { createServer } from "node:http";
import { HttpResponse, http } from "msw";
import { createMiddleware } from "@mswjs/http-middleware";
import { getRequestListener } from "@hono/node-server";
import { Hono } from "hono";
import { pubchemHandlers } from "../../modules/pubchem/__tests__/pubchemMock/handlers.mjs";
import { fieldmarkHandlers } from "../../modules/fieldmark/__tests__/fieldmarkMock/handlers.mjs";

const PORT = Number(process.env.E2E_MOCK_PORT ?? "9099");

/**
 * Health probe — Playwright's webServer.url check hits GET /e2e-health to
 * confirm the server is up before running any tests.
 */
const healthHandler = http.get("/e2e-health", () =>
  new HttpResponse("ok", { headers: { "Content-Type": "text/plain" } }),
);

const handlers = [healthHandler, ...pubchemHandlers, ...fieldmarkHandlers];
const mswMiddleware = createMiddleware(...handlers);

const app = new Hono();
app.notFound((c) => c.text("not found", 404));
const honoListener = getRequestListener(app.fetch);

createServer((req, res) => {
  req.protocol = "http";
  req.get = req.header = (name) => req.headers[String(name).toLowerCase()];
  mswMiddleware(req, res, (err) => {
    if (err) {
      res.statusCode = 500;
      res.end(String(err));
      return;
    }
    honoListener(req, res);
  });
}).listen(PORT, "127.0.0.1", () => {
  console.log(`[e2e-mocks] listening on http://127.0.0.1:${PORT}`);
});
