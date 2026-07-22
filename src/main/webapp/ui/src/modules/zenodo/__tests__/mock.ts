import { HttpResponse, http } from "msw";
import depositionFixture from "./fixtures/deposition.json" with { type: "json" };
import fileFixture from "./fixtures/file.json" with { type: "json" };

function depositionFor(request: Request) {
  const origin = new URL(request.url).origin;
  return {
    ...depositionFixture,
    links: Object.fromEntries(
      Object.entries(depositionFixture.links).map(([name, url]) => [name, `${origin}${new URL(url).pathname}`]),
    ),
  };
}

function fileFor(request: Request) {
  const origin = new URL(request.url).origin;
  return JSON.parse(JSON.stringify(fileFixture).replaceAll("http://localhost:9099", origin));
}

export const zenodoHandlers = [
  http.get("/deposit/depositions", ({ request }) => HttpResponse.json([depositionFor(request)])),

  http.post("/deposit/depositions", ({ request }) => HttpResponse.json(depositionFor(request))),

  http.put("/files/:bucketId/:filename", ({ request }) => HttpResponse.json(fileFor(request))),
];
