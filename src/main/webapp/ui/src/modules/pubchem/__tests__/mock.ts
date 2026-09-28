import { HttpResponse, http } from "msw";
import reactionMoleculeFixtures from "./fixtures/reactionMolecules.json" with { type: "json" };
import riboflavinSearchFixture from "./fixtures/riboflavinSearch.json" with { type: "json" };
import riboflavinSynonymsFixture from "./fixtures/riboflavinSynonyms.json" with { type: "json" };
import searchFixture from "./fixtures/search.json" with { type: "json" };
import synonymsFixture from "./fixtures/synonyms.json" with { type: "json" };

const PNG_1x1 = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

// Keyed by search identifier so tests get distinct compounds (and a genuine 404) instead of
// one fixture for everything. Benzene/cyclopentadiene/cyclohexane are keyed by the exact
// SMILES the chemistry service returns for basic_reaction.cdxml's reactants, which
// StoichiometryManagerImpl.createFromAnalysis looks up by SMILES for a display name.
const SEARCH_FIXTURES_BY_IDENTIFIER: Record<string, unknown> = {
  aspirin: searchFixture,
  "cc(=o)oc1=cc=cc=c1c(=o)o": searchFixture,
  "83-88-5": riboflavinSearchFixture,
  "c1=cc=cc=c1": reactionMoleculeFixtures.benzene,
  "c1c=cc=c1": reactionMoleculeFixtures.cyclopentadiene,
  c1ccccc1: reactionMoleculeFixtures.cyclohexane,
};

const SYNONYMS_FIXTURES_BY_CID: Record<string, unknown> = {
  "2244": synonymsFixture,
  "493570": riboflavinSynonymsFixture,
};

export const pubchemHandlers = [
  http.get("/rest/pug/compound/:namespace/:identifier/property/:properties/json", ({ params }) => {
    const identifier = decodeURIComponent(String(params.identifier)).toLowerCase();
    const fixture = SEARCH_FIXTURES_BY_IDENTIFIER[identifier];
    return fixture ? HttpResponse.json(fixture) : new HttpResponse(null, { status: 404 });
  }),

  http.get("/rest/pug/compound/cid/:cid/synonyms/JSON", ({ params }) => {
    const fixture = SYNONYMS_FIXTURES_BY_CID[String(params.cid)];
    return fixture ? HttpResponse.json(fixture) : new HttpResponse(null, { status: 404 });
  }),

  http.get("/image/imgsrv.fcgi", () => new HttpResponse(PNG_1x1, { headers: { "Content-Type": "image/png" } })),
];
