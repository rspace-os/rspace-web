import { HttpResponse, http } from "msw";
import animalsFixture from "./fixtures/animals.json" with { type: "json" };
import locationsFixture from "./fixtures/locations.json" with { type: "json" };
import versionFixture from "./fixtures/version.json" with { type: "json" };

const animalTemplate = animalsFixture[0];
const totalCount = 137;
const animals = Array.from({ length: totalCount }, (_, index) => ({
  ...animalTemplate,
  eartag_or_id: `MOCK-${String(index + 1).padStart(3, "0")}`,
  age_days: animalTemplate.age_days + index,
  labid: `LAB-${index + 1}`,
}));

const animalResponse = ({ request }: { request: Request }) => {
  const params = new URL(request.url).searchParams;
  const limit = Number(params.get("l") ?? "10");
  const offset = Number(params.get("o") ?? "0");

  return HttpResponse.json(animals.slice(offset, offset + limit), {
    headers: { "X-Total-Count": String(totalCount) },
  });
};

export const pyratHandlers = [
  http.get("/version", () => HttpResponse.json(versionFixture)),
  http.get("/locations", () => HttpResponse.json(locationsFixture)),
  http.get("/animals", animalResponse),
  http.get("/pups", animalResponse),
];
