import { HttpResponse, http } from "msw";
import animalsFixture from "./fixtures/animals.json" with { type: "json" };
import locationsFixture from "./fixtures/locations.json" with { type: "json" };
import versionFixture from "./fixtures/version.json" with { type: "json" };

const animalResponse = () =>
  HttpResponse.json(animalsFixture, { headers: { "X-Total-Count": String(animalsFixture.length) } });

export const pyratHandlers = [
  http.get("/version", () => HttpResponse.json(versionFixture)),
  http.get("/locations", () => HttpResponse.json(locationsFixture)),
  http.get("/animals", animalResponse),
  http.get("/pups", animalResponse),
];
