import { HttpResponse, http } from "msw";

let nextDoi = 1;

type DoiState = "draft" | "findable" | "registered";
type DoiData = {
  id?: string;
  attributes?: {
    prefix?: string;
    event?: string;
    doi?: string;
    state?: DoiState;
  };
};

function responseData(requestData: DoiData, state: DoiState) {
  const id = requestData.id ?? `10.99999/e2e-igsn-${nextDoi++}`;
  return {
    ...requestData,
    id,
    type: "dois",
    attributes: {
      ...requestData.attributes,
      doi: id,
      state,
    },
  };
}

export const dataciteHandlers = [
  http.get("/heartbeat", () => new HttpResponse("OK")),
  http.get("/client-prefixes", () => HttpResponse.json({ meta: { total: 1 } })),
  http.post("/dois", async ({ request }) => {
    const body = (await request.json()) as { data?: DoiData };
    const requestData = body.data;
    if (!requestData?.attributes?.prefix) {
      return HttpResponse.json({ errors: [{ status: "403" }] }, { status: 403 });
    }
    return HttpResponse.json({ data: responseData(requestData, "draft") }, { status: 201 });
  }),
  // DOI paths contain separate prefix and suffix segments.
  http.put("/dois/:prefix/:suffix", async ({ request, params }) => {
    const body = (await request.json()) as { data: DoiData };
    const id = `${params.prefix}/${params.suffix}`;
    const event = body.data?.attributes?.event;
    const state = event === "publish" ? "findable" : event === "hide" ? "registered" : "draft";
    return HttpResponse.json({ data: responseData({ ...body.data, id }, state) });
  }),
  http.delete("/dois/:prefix/:suffix", () => new HttpResponse(null, { status: 204 })),
];
