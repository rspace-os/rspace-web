// Generic e2e mock server. Aggregates per-integration MSW handlers behind one
// HTTP endpoint that the backend is pointed at (per-integration *.base.url, e.g.
// -Dpubchem.base.url). Started as a Playwright webServer in mock mode.
//
// MSW handlers run as Express-style middleware (@mswjs/http-middleware) hosted by
// Hono (@hono/node-server's getRequestListener provides the fallback + listener);
// this version doesn't export RESPONSE_ALREADY_SENT, so the middleware is composed
// at the listener rather than inside Hono's app.use chain.
//
// Add an integration: import its handler array and spread it into `handlers`.
import { createServer } from "node:http";
import { getRequestListener } from "@hono/node-server";
import { createMiddleware } from "@mswjs/http-middleware";
import { Hono } from "hono";
import { pubchemHandlers } from "../../modules/pubchem/__tests__/pubchemMock/handlers.mjs";

const handlers = [...pubchemHandlers];

const mswMiddleware = createMiddleware(...handlers);

const app = new Hono();
app.notFound((c) => c.text("not found", 404));
const honoListener = getRequestListener(app.fetch);

const port = Number(process.env.E2E_MOCK_PORT ?? 9099);
createServer((req, res) => {
  // Minimal Express-compat shim: the MSW middleware reads req.protocol and
  // req.get/req.header, which raw Node IncomingMessage doesn't provide.
  req.protocol = "http";
  req.get = req.header = (name) => req.headers[String(name).toLowerCase()];
  // MSW first; anything it doesn't handle falls through to Hono.
  mswMiddleware(req, res, (err) => {
    if (err) {
      res.statusCode = 500;
      res.end(String(err));
      return;
    }
    honoListener(req, res);
  });
}).listen(port, () => console.log(`[e2e-mocks] listening on http://localhost:${port}`));
