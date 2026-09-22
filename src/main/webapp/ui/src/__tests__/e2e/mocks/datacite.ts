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
    titles?: Array<{ title?: string }>;
  };
};

/**
 * A suffix starting with this marker resolves as an already-findable Instrument DOI - Findable state
 * plus an Instrument resourceType, both required by `PidinstLookupManagerImpl.fetchByPid`. A marker,
 * not one fixed DOI, so parallel runs each mint their own and never collide.
 */
export const IMPORTABLE_DOI_PREFIX = "10.5072";
export const IMPORTABLE_DOI_SUFFIX_PREFIX = "e2e-pidinst-import";

function importableDoiRecord(doi: string, suffix: string) {
  return {
    id: doi,
    type: "dois",
    attributes: {
      doi,
      state: "findable" as DoiState,
      titles: [{ title: `E2E Import Target ${suffix}` }],
      types: { resourceType: "Instrument", resourceTypeGeneral: "Instrument" },
      contributors: [{ contributorType: "HostingInstitution", name: "E2E Test Institution" }],
      creators: [{ name: "E2E Instrument Co" }],
      url: "https://example.com/e2e-pidinst-fixture",
    },
  };
}

/** A magic word in the outbound title that makes the PUT below fail - a real outage on demand. */
export const FORCE_EXTERNAL_UPDATE_FAILURE_SENTINEL = "e2e-pidinst-forcefail";

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

// The DOI retrieve-by-id call (PidinstLookupManagerImpl.fetchByPid, both direct-PID search and
// import re-fetch). Real DataCite's own URI template (retrieveDoi's "/dois/{doi}/?affiliation=true")
// carries a trailing slash before the query string, so both shapes need a handler.
function getDoiById({ params }: { params: { prefix: string; suffix: string } }) {
  const { prefix, suffix } = params;
  if (prefix === IMPORTABLE_DOI_PREFIX && suffix.startsWith(IMPORTABLE_DOI_SUFFIX_PREFIX)) {
    return HttpResponse.json({ data: importableDoiRecord(`${prefix}/${suffix}`, suffix) });
  }
  return new HttpResponse(null, { status: 404 });
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
  http.get("/dois/:prefix/:suffix", getDoiById),
  http.get("/dois/:prefix/:suffix/", getDoiById),
  // DOI paths contain separate prefix and suffix segments.
  http.put("/dois/:prefix/:suffix", async ({ request, params }) => {
    const body = (await request.json()) as { data: DoiData };
    if (body.data?.attributes?.titles?.[0]?.title?.includes(FORCE_EXTERNAL_UPDATE_FAILURE_SENTINEL)) {
      return HttpResponse.json({ errors: [{ status: "500", title: "e2e forced failure" }] }, { status: 500 });
    }
    const id = `${params.prefix}/${params.suffix}`;
    const event = body.data?.attributes?.event;
    const state = event === "publish" ? "findable" : event === "hide" ? "registered" : "draft";
    return HttpResponse.json({ data: responseData({ ...body.data, id }, state) });
  }),
  http.delete("/dois/:prefix/:suffix", () => new HttpResponse(null, { status: 204 })),
];
