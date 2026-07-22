import { HttpResponse, http } from "msw";
import searchFixture from "./fixtures/search.json" with { type: "json" };
import synonymsFixture from "./fixtures/synonyms.json" with { type: "json" };

const PNG_1x1 = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

export const pubchemHandlers = [
  http.get("/rest/pug/compound/:namespace/:identifier/property/:properties/json", () =>
    HttpResponse.json(searchFixture),
  ),

  http.get("/rest/pug/compound/cid/:cid/synonyms/JSON", () => HttpResponse.json(synonymsFixture)),

  http.get("/image/imgsrv.fcgi", () => new HttpResponse(PNG_1x1, { headers: { "Content-Type": "image/png" } })),
];
