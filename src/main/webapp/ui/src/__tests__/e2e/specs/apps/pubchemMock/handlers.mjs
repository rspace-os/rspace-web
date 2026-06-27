/**
 * PubChem mock handlers for the e2e mock server.
 *
 * Covers every HTTP call the Java PubchemSearcher makes when the browser
 * triggers a compound search:
 *
 *   1. Property search  GET /rest/pug/compound/{type}/{term}/property/.../json
 *      → PubchemResponse (Title, SMILES, MolecularFormula, CID)
 *
 *   2. Synonym lookup   GET /rest/pug/compound/cid/{cid}/synonyms/JSON
 *      → PubchemSynonymsResponse (used to extract the CAS number)
 *
 *   3. Structure image  GET /image/imgsrv.fcgi?cid={cid}&t=l
 *      → PNG image; the backend stores the URL as-is and the browser fetches
 *        it directly. We return a 1×1 transparent PNG so tests don't get
 *        broken <img> elements.
 *
 * Fixtures in ./fixtures/ are harvested from the real PubChem API.
 * They are the single source of truth: the mock returns them AND the real-mode
 * e2e assertions compare against the same values (drift detection).
 *
 * Handler format: { method, match(pathname) → boolean, respond(req) → { status, body, contentType? } }
 * body can be a plain object (JSON), string, or Buffer (binary).
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const fixturesDir = fileURLToPath(new URL("./fixtures/", import.meta.url));

const searchFixture = JSON.parse(readFileSync(`${fixturesDir}search.json`, "utf8"));
const synonymsFixture = JSON.parse(readFileSync(`${fixturesDir}synonyms.json`, "utf8"));

// 1×1 transparent PNG — placeholder for compound structure thumbnails.
// Avoids broken <img> tags in the UI without shipping a real compound image.
const PNG_1x1 = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

export const pubchemHandlers = [
  // Compound property search (name / CID / SMILES / InChI namespace)
  {
    method: "GET",
    match: (pathname) =>
      /^\/rest\/pug\/compound\/[^/]+\/[^/]+\/property\/[^/]+\/json$/i.test(pathname),
    respond: () => ({ status: 200, body: searchFixture }),
  },

  // CAS / synonym lookup for a given CID
  {
    method: "GET",
    match: (pathname) =>
      /^\/rest\/pug\/compound\/cid\/[^/]+\/synonyms\/JSON$/i.test(pathname),
    respond: () => ({ status: 200, body: synonymsFixture }),
  },

  // Structure thumbnail — browser <img> src points here
  {
    method: "GET",
    match: (pathname) => pathname === "/image/imgsrv.fcgi",
    respond: () => ({ status: 200, body: PNG_1x1, contentType: "image/png" }),
  },
];
