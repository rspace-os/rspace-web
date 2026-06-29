#!/usr/bin/env node
/**
 * E2E mock server — a real HTTP server on port 9099.
 *
 * WHY a real HTTP server and not MSW's Node interceptor
 * RSpace integration calls are made server-side by the Java backend
 * (Spring RestTemplate). The browser only calls /api/v1/<integration>/...
 * on the RSpace server — it never reaches PubChem, GitHub, etc. directly.
 *
 * MSW's msw/node patches Node's http module to intercept outgoing requests
 * from the same Node process — it cannot intercept a Java JVM's network calls.
 *
 * Instead, we start a real TCP listener. JVM startup args override every
 * integration's base URL (pubchem.base.url, github.api.base.url, …) to point
 * at http://localhost:9099, so Spring RestTemplate calls land here.
 *
 * HOW it works
 * Each integration exports a `handlers` array of plain objects:
 *
 *   { method: "GET", match: (pathname) => boolean, respond: (req) => { status, body } }
 *
 * body is a plain object (sent as JSON) or a string (sent as-is).
 * ALL_HANDLERS is assembled by spreading each integration's array below.
 *
 * ADDING AN INTEGRATION
 * 1. Create  src/modules/<name>/__tests__/<name>Mock/handlers.mjs
 * 2. Add its fixture JSON files under  …/<name>Mock/fixtures/
 * 3. Import and spread its handlers into ALL_HANDLERS here.
 *
 * USAGE
 *  node src/__tests__/e2e/mockServer.mjs # default port 9099
 *      `E2E_MOCK_PORT=9100 node ... # override port
 *
 * Playwright's webServer block starts this automatically in mock mode.
 * See playwright-e2e.config.ts → webServer.
 */

import {createServer} from "node:http";

const PORT = Number(process.env.E2E_MOCK_PORT ?? "9099");

import {pubchemHandlers} from "./specs/apps/pubchemMock/handlers.mjs";
import {fieldmarkHandlers} from "./specs/apps/fieldmarkMock/handlers.mjs";

/**
 * Health probe handler — must stay first.
 * Playwright's webServer.url check hits GET /e2e-health to confirm the server
 * is up before running any tests.
 */
const healthHandler = {
    method: "GET",
    match: (pathname) => pathname === "/e2e-health",
    respond: () => ({status: 200, body: "ok", contentType: "text/plain"}),
};

const ALL_HANDLERS = [
    healthHandler,
    ...pubchemHandlers,
];

const server = createServer((req, res) => {
    const pathname = new URL(req.url ?? "/", `http://localhost:${PORT}`).pathname;

    const handler = ALL_HANDLERS.find(
        (h) => h.method === req.method && h.match(pathname),
    );

    if (handler) {
        const {status, body, contentType} = handler.respond(req);
        const isBuffer = Buffer.isBuffer(body);
        const isText = typeof body === "string";
        res.writeHead(status, {
            "Content-Type":
                contentType ?? (isBuffer ? "application/octet-stream" : isText ? "text/plain" : "application/json"),
        });
        res.end(isBuffer || isText ? body : JSON.stringify(body));
    } else {
        console.warn(`[e2e-mocks] unhandled: ${req.method} ${req.url}`);
        res.writeHead(404, {"Content-Type": "text/plain"});
        res.end("not found");
    }
});

server.listen(PORT, "127.0.0.1", () => {
    console.log(`[e2e-mocks] listening on http://127.0.0.1:${PORT}`);
});
