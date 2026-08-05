import { randomUUID } from "node:crypto";
import { HttpResponse, http } from "msw";
import historyFixture from "./fixtures/history.json" with { type: "json" };
import historyDatasetAssociationFixture from "./fixtures/historyDatasetAssociation.json" with { type: "json" };
import historyDatasetCollectionAssociationFixture from "./fixtures/historyDatasetCollectionAssociation.json" with {
  type: "json",
};
import uploadFileResponseFixture from "./fixtures/uploadFileResponse.json" with { type: "json" };

const TUS_HEADERS = { "Tus-Resumable": "1.0.0" };

async function uploadChunkResolver({ request }: { request: Request }) {
  const body = await request.arrayBuffer();
  return new HttpResponse(null, {
    status: 204,
    headers: { ...TUS_HEADERS, "Upload-Offset": String(body.byteLength) },
  });
}

// GalaxyService appends `/api` to the configured server URL.
export const galaxyHandlers = [
  http.post("/api/upload/resumable_upload", ({ request }) => {
    const sessionId = randomUUID();
    const origin = new URL(request.url).origin;
    return new HttpResponse(null, {
      status: 201,
      headers: { ...TUS_HEADERS, Location: `${origin}/api/upload/resumable_upload/${sessionId}` },
    });
  }),

  // Galaxy's client was observed sending POST chunks despite standard TUS using PATCH.
  http.patch("/api/upload/resumable_upload/:sessionId", uploadChunkResolver),
  http.post("/api/upload/resumable_upload/:sessionId", uploadChunkResolver),

  http.post("/api/histories", async ({ request }) => {
    const params = new URLSearchParams(await request.text());
    const name = params.get("name") ?? historyFixture.name;
    return HttpResponse.json({ ...historyFixture, name });
  }),

  http.post("/api/tools/fetch", () => HttpResponse.json(uploadFileResponseFixture)),

  http.put("/api/histories/:historyId/contents/datasets/:datasetId", () =>
    HttpResponse.json(historyDatasetAssociationFixture),
  ),

  http.post("/api/dataset_collections", () => HttpResponse.json(historyDatasetCollectionAssociationFixture)),

  http.get("/api/invocations", () => HttpResponse.json([])),
];
