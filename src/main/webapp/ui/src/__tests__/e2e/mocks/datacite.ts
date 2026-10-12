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

// Keep the metadata RSpace actually sent, keyed by DOI so scenarios cannot read one another's publications.
const dois = new Map<string, ReturnType<typeof responseData>>();

export const dataciteHandlers = [
  http.get("/heartbeat", () => new HttpResponse("OK")),
  http.get("/client-prefixes", () => HttpResponse.json({ meta: { total: 1 } })),
  http.post("/dois", async ({ request }) => {
    const body = (await request.json()) as { data?: DoiData };
    const requestData = body.data;
    if (!requestData?.attributes?.prefix) {
      return HttpResponse.json({ errors: [{ status: "403" }] }, { status: 403 });
    }
    const data = responseData(requestData, "draft");
    dois.set(data.id, data);
    return HttpResponse.json({ data }, { status: 201 });
  }),
  // DOI paths contain separate prefix and suffix segments.
  http.put("/dois/:prefix/:suffix", async ({ request, params }) => {
    const body = (await request.json()) as { data: DoiData };
    const id = `${params.prefix}/${params.suffix}`;
    const event = body.data?.attributes?.event;
    const state = event === "publish" ? "findable" : event === "hide" ? "registered" : "draft";
    const data = responseData({ ...body.data, id }, state);
    dois.set(id, data);
    return HttpResponse.json({ data });
  }),
  http.get("/dois/:prefix/:suffix", ({ params }) => {
    const data = dois.get(`${params.prefix}/${params.suffix}`);
    return data
      ? HttpResponse.json({ data })
      : HttpResponse.json({ errors: [{ status: "404", title: "DOI not found" }] }, { status: 404 });
  }),
  http.delete("/dois/:prefix/:suffix", ({ params }) => {
    dois.delete(`${params.prefix}/${params.suffix}`);
    return new HttpResponse(null, { status: 204 });
  }),
];
