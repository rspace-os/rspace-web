// MSW request handlers for the fake PubChem PUG REST API. Serve the harvested
// raw shapes (the single source of truth, shared with real-mode drift checks).
// Aggregated into the generic e2e mock server (src/__tests__/e2e/mockServer.mjs).
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { HttpResponse, http } from "msw";

const dir = fileURLToPath(new URL("./fixtures/", import.meta.url));
const search = JSON.parse(readFileSync(`${dir}search.json`, "utf8"));
const synonyms = JSON.parse(readFileSync(`${dir}synonyms.json`, "utf8"));
// 1x1 transparent PNG — the structure thumbnail <img> only needs a valid image.
const png = Uint8Array.from(
  atob(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC",
  ),
  (ch) => ch.charCodeAt(0),
);

export const pubchemHandlers = [
  // GET /rest/pug/compound/{namespace}/{identifier}/property/{props}/json
  http.get("*/rest/pug/compound/:namespace/:identifier/property/:props/json", () =>
    HttpResponse.json(search),
  ),
  // GET /rest/pug/compound/cid/{cid}/synonyms/JSON
  http.get("*/rest/pug/compound/cid/:cid/synonyms/JSON", () => HttpResponse.json(synonyms)),
  // GET /image/imgsrv.fcgi?cid=...  (structure thumbnail)
  http.get("*/image/imgsrv.fcgi", () =>
    HttpResponse.arrayBuffer(png.buffer, { headers: { "Content-Type": "image/png" } }),
  ),
];
